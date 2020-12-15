package com.mightylama.runblind

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebViewClient
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mapbox.geojson.FeatureCollection
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdate
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.*
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineJoin
import com.mapbox.mapboxsdk.utils.BitmapUtils
import com.mapbox.mapboxsdk.utils.ColorUtils
import com.mightylama.runblind.databinding.ActivityMainBinding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.network.sockets.ConnectTimeoutException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType

class MainActivity
    : FragmentActivity(),
    CircuitListFragment.CircuitListCallback, RecordFragment.RecordFragmentCallback, CompassFragment.CompassFragmentCallback {

    private val KEY_IP = "key_ip"
    private val KEY_MARKER = "key_marker"


    private val KEY_API_MAPBOX_DOWNLOAD = BuildConfig.MAPBOX_KEY_DOWNLOAD

    private lateinit var binding: ActivityMainBinding
    private val circuitList = ArrayList<String>()
    private var circuitListFragment: CircuitListFragment? = null
    private var recordFragment: RecordFragment? = null
    private var compassFragment: CompassFragment? = null

    private var targetZoomForCenterCamera = -1
    private var lastPosition: LatLng? = null

    private var lineManager : LineManager? = null
    private var pathLine : Line? = null
    private var symbolManager : SymbolManager? = null
    private var symbol : Symbol? = null

    private var lastSelectedCircuit = 0
    private var isRecording = false
    private var mapbox : MapboxMap? = null

    private var isPagerEnabled: Boolean = false
        set(value){
            field = value
            binding.apply {
                pager.isUserInputEnabled = value
                val tabStrip = tab.getChildAt(0).also { it.isEnabled = value } as ViewGroup
                tabStrip.forEach {
                    it.isEnabled = value
                }
            }
        }


    private var baseUrl: String? = null
    private val httpClient = HttpClient()
    override var serverState = ServerState.Undefined

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pingRunnable = object : Runnable {
        override fun run() {
            baseUrl?.let {
                GlobalScope.launch { getSpatialData() }
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pager.let {
            it.adapter = MainFragmentStateAdapter(this)
            it.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    when (position) {
                        0 -> {
                            targetZoomForCenterCamera = -1
                            if (serverState == ServerState.Connected) GlobalScope.launch {
                                getCircuitList()
                            }
                        }
                        1 -> {
                            removePath()
                            targetZoomForCenterCamera = 17
                            lastPosition?.let { setView(it) }

                        }
                        2 -> {
                            removePath()
                            targetZoomForCenterCamera = 17
                            lastPosition?.let { setView(it) }
                        }
                    }
                }
            })
        }

        TabLayoutMediator(binding.tab, binding.pager) { tab, position ->
            when(position) {
                0 -> tab.text = "Guiding"
                1 -> tab.text = "Recording"
                2 -> tab.text = "Compass"
            }
        }.attach()

        binding.volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}

            override fun onStartTrackingTouch(p0: SeekBar?) {}

            override fun onStopTrackingTouch(p0: SeekBar?) {
                p0?.let {
                    GlobalScope.launch { setVolume(it.progress) }
                }
            }
        })


        showIpDialog()
        configureMap(savedInstanceState)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(pingRunnable)
        binding.map.onPause()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(pingRunnable)
        binding.map.onResume()
    }

    override fun onStart() {
        super.onStart()
        binding.map.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.map.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.map.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.map.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.map.onDestroy()
    }




    class MainFragmentStateAdapter(private var mainActivity: MainActivity): FragmentStateAdapter(mainActivity) {
        override fun getItemCount(): Int {
            return 3
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> CircuitListFragment(mainActivity, mainActivity.circuitList).also { mainActivity.circuitListFragment = it }
                1 -> RecordFragment(mainActivity).also { mainActivity.recordFragment = it }
                2 -> CompassFragment(mainActivity).also { mainActivity.compassFragment = it }
                else -> Fragment()
            }
        }
    }

    private fun populateDummyCircuits() {
        updateCircuitList(listOf("Circuit 1", "Circuit 2", "Circuit 3"))
    }


    private suspend fun getSpatialData()
    {
        try {
            val response = httpClient.get<String>(baseUrl + "get_spatial_data")

            runOnUiThread {
                if (serverState != ServerState.Connected)
                    onServerConnected()

                val spatialData = SpatialData()
                response.split(",")
                    .also {
                        it.subList(0,3).map { it.toInt() }
                            .let { spatialData.apply {
                                yaw = it[0]
                                pitch = it[1]
                                roll = it[2]
                            }}
                    }
                    .also {
                        it.subList(3,5).map { it.toDouble() }
                            .let { spatialData.position.apply {
                                latitude = it[0]
                                longitude = it[1]
                            }}
                    }

                    lastPosition = spatialData.position
                        .also {
                            if (isRecording) { addPointToPath(it) }
                        }

                    updatePosition(spatialData.position, spatialData.yaw)
                    updateDebugData(spatialData)
            }
        }

        catch (exception: Exception) {
            runOnUiThread {
                if (serverState != ServerState.Disconnected)
                    onServerDisconnected()

                when (exception) {
                    is UnresolvedAddressException -> Toast.makeText(this, "Please enter a valid IP", Toast.LENGTH_SHORT).show()
                    is ConnectTimeoutException -> "" //TODO eventually
                }
            }
        }
    }

    private fun onServerDisconnected()
    {
        serverState = ServerState.Disconnected
        binding.dot.imageTintList = getColorStateList(R.color.dotColorDisconnected)
        onWaiting()
    }

    private fun onWaiting()
    {
        circuitListFragment?.onCircuitWaiting()
        recordFragment?.onRecordWaiting()
        compassFragment?.onCompassWaiting()
        binding.volumeSlider.isEnabled = false
    }

    private fun onServerConnected()
    {
        serverState = ServerState.Connected
        binding.dot.imageTintList = getColorStateList(R.color.dotColorConnected)
        circuitListFragment?.onCircuitStopped()
        recordFragment?.onRecordStopped()
        compassFragment?.onCompassStopped()

        GlobalScope.launch {
            getVolume()
            getCircuitList()
            getCircuitPath(0)
        }
    }



    private suspend fun getCircuitList() {
        getFromServer("get_circuit_list")
            ?.let { runOnUiThread {
                updateCircuitList(it)
                circuitListFragment?.binding?.spinner?.apply {
                    lastSelectedCircuit = min(lastSelectedCircuit, adapter.count - 1)
                        .also { setSelection(it) }
                        .also { GlobalScope.launch { getCircuitPath(it) }}
                }
            } }
    }

    private suspend fun getVolume(){
        val volume = getFromServer("get_volume")
        volume?.let {
            runOnUiThread { updateVolume(volume.toInt()) }
        }
    }

    private suspend fun setVolume(volume: Int){
        getFromServer("set_volume/$volume")
    }

    override suspend fun startCircuit(circuitIndex: Int) {
        getFromServer("start_circuit/$circuitIndex")
        runOnUiThread {
            targetZoomForCenterCamera = 20
            lastPosition?.let { setView(it) }
            isPagerEnabled = false
            circuitListFragment?.onCircuitStarted()
        }
    }

    override suspend fun stopCircuit() {
        getFromServer("stop_circuit")
        runOnUiThread {
            targetZoomForCenterCamera = -1
            fitBounds()
            isPagerEnabled = true
            circuitListFragment?.onCircuitStopped()
        }
    }

    override suspend fun startRecording(namePath: String) {
        getFromServer("start_recording/$namePath")
        runOnUiThread {
            removePath()
            isRecording = true
            isPagerEnabled = false
            recordFragment?.onRecordStarted()
        }
    }

    override suspend fun stopRecording() {
        isRecording = false
        getFromServer("stop_recording")
        runOnUiThread {
            removePath()
            isPagerEnabled = true
            circuitListFragment?.binding?.spinner?.adapter?.count?.let { lastSelectedCircuit = it }
            recordFragment?.onRecordStopped()
        }
    }

    override suspend fun getCircuitPath(index: Int) {
        lastSelectedCircuit = index
        val response = getFromServer("get_circuit_path/$index")
        response?.let {
            if (binding.pager.currentItem == 0) runOnUiThread {
                updatePath(it.removePrefix("[").removeSuffix("]").split("],[").map { deserializePoint(it) } )
                fitBounds()
            }
        }
    }

    override suspend fun startCompass() {
        getFromServer("start_compass")
        runOnUiThread {
            isPagerEnabled = false
            compassFragment?.onCompassStarted()
        }
    }

    override suspend fun stopCompass() {
        getFromServer("stop_compass")
        runOnUiThread {
            isPagerEnabled = true
            compassFragment?.onCompassStopped()
        }
    }


    private suspend fun getFromServer(path: String) : String?{
        return try {
            httpClient.get(baseUrl + path)
        } catch (exception: Exception) {
            runOnUiThread { Toast.makeText(this, exception.toString(), Toast.LENGTH_SHORT).show() }
            null
        }
    }


    private fun updateDebugData(spatialData: SpatialData) {

        binding.apply {

            { textView: TextView, data: Any ->
                textView.text = data.toString()
            }
                .also { it(yawCount, spatialData.yaw) }
                .also { it(pitchCount, spatialData.pitch) }
                .also { it(rollCount, spatialData.roll) }
                .also { it(latCount, spatialData.position.latitude) }
                .also { it(lonCount, spatialData.position.longitude) }
        }
    }

    private fun updateVolume(volume: Int) {
        binding.volumeSlider.apply {
            progress = volume
            isEnabled = true
        }
    }

    private fun updateCircuitList(serializedNameList: String) {
        updateCircuitList(serializedNameList.split(","))
    }

    private fun updateCircuitList(nameList: List<String>) {
        circuitList.apply {
            clear()
            addAll(nameList)
        }
        runOnUiThread { circuitListFragment?.notifyDataChanged() }
    }

    private fun showIpDialog() {
        val editText = TextInputEditText(this)
        editText.setText(getPreferences(Context.MODE_PRIVATE).getString(KEY_IP, ""), TextView.BufferType.EDITABLE)
        AlertDialog.Builder(this)
            .setMessage("Please enter server IP")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("OK") { _: DialogInterface, _: Int ->
                val address = editText.text.toString()
                baseUrl = "http://$address:5000/"
                getPreferences(Context.MODE_PRIVATE).edit().putString(KEY_IP, address).apply()
            }
            .create()
            .show()
    }

    enum class ServerState{
        Connected, Disconnected, Undefined
    }






    // FOR MAP

    private fun configureMap(savedInstanceState: Bundle?) {

        binding.map.apply {
            onCreate(savedInstanceState)
            getMapAsync {
                mapbox = it.also {
                    it.setStyle(Style.MAPBOX_STREETS) { style: Style ->

                        it.uiSettings.isRotateGesturesEnabled = false

                        BitmapUtils.getBitmapFromDrawable(ContextCompat.getDrawable(context, R.drawable.marker))
                            ?.let { style.addImage(KEY_MARKER, it) }

                        lineManager = LineManager(this, it, style)
                        pathLine = lineManager?.create(
                            LineOptions()
                                .withLatLngs(MutableList(0) {LatLng()})
                                .withLineWidth(5F)
                                .withLineColor(ColorUtils.colorToRgbaString(getColor(R.color.circuitPathColor)))
                        )
                        symbolManager = SymbolManager(this, it, style)
                        symbolManager?.apply {
                            iconIgnorePlacement = true
                            iconAllowOverlap = true
                        }
                        symbol = symbolManager?.create(
                            SymbolOptions()
                                .withLatLng(LatLng())
                                .withIconOpacity(0F)
                                .withIconImage(KEY_MARKER)
                                .withIconSize(0.8F)
                        )
                    }
                }
            }
        }

    }


    private fun setView(point: LatLng, zoom: Int) {
        mapbox?.easeCamera(CameraUpdateFactory.newLatLngZoom(point, zoom.toDouble()))
    }

    private fun setView(point: LatLng) {
        mapbox?.easeCamera(CameraUpdateFactory.newLatLngZoom(point, targetZoomForCenterCamera.toDouble()))
    }


        private fun updatePosition(point: LatLng, yaw : Int, immediatePan: Boolean = false) {
        symbol?. apply {
            iconOpacity = 1F
            latLng = point
            iconRotate = -yaw.toFloat()
        }
        symbolManager?.update(symbol)

        if (targetZoomForCenterCamera != -1) {
            mapbox?.easeCamera(CameraUpdateFactory.newLatLngZoom(point, targetZoomForCenterCamera.toDouble()))
        }
    }


    private fun updatePath(pointList: List<LatLng>?) {
        pathLine?.apply {
            pointList?.let { latLngs = it }
            lineManager?.update(this)
        }
    }


    private fun removePath() {
        pathLine?.apply {
            latLngs = ArrayList()
            lineManager?.update(this) }
    }

    private fun addPointToPath(point : LatLng) {
        pathLine?.apply {
            latLngs = latLngs.toMutableList().also { it.add(point) }
            lineManager?.update(this)
        }
    }

    private fun fitBounds() {
        pathLine?.latLngs?.let {
            if (it.isNotEmpty()) {
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.Builder().includes(it).build(),
                    100
                ).let {cam ->
                    mapbox?.let {
                        if (it.cameraPosition.zoom > 5F)
                            it.easeCamera(cam, 500)
                        else
                            it.moveCamera(cam)
                    }
                }
            }
        }
    }


    private fun deserializePoint(serializedPoint : String) : LatLng {
        val point = LatLng()
        serializedPoint.removePrefix("[").removeSuffix("]").split(",").let {
            if (it.size == 2) {
                point.latitude = it[0].toDouble()
                point.longitude = it[1].toDouble()
            }
        }
        return point
    }


    class SpatialData(var yaw: Int = 0, var pitch: Int = 0, var roll: Int = 0, var position: LatLng = LatLng())
}