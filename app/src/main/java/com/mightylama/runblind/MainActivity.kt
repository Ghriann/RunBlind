package com.mightylama.runblind

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebViewClient
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mightylama.runblind.databinding.ActivityMainBinding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.network.sockets.ConnectTimeoutException
import io.ktor.util.network.UnresolvedAddressException
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

    private lateinit var binding: ActivityMainBinding
    private val circuitList = ArrayList<String>()
    private var circuitListFragment: CircuitListFragment? = null
    private var recordFragment: RecordFragment? = null
    private var compassFragment: CompassFragment? = null

    private var centerMapOnPosition = false
    private var lastPosition: Point? = null

    private var lastSelectedCircuit = 0
    private var isRecording = false
    private val recordingPath = LinkedList<Point>()

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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pager.let {
            it.adapter = MainFragmentStateAdapter(this)
            it.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    when (position) {
                        0 -> {
                            centerMapOnPosition = false
                            if (serverState == ServerState.Connected) GlobalScope.launch {
                                getCircuitList()
                            }
                        }
                        1 -> {
                            removePath()
                            centerMapOnPosition = true
                            lastPosition?.let { setView(it, 17) }

                        }
                        2 -> {
                            removePath()
                            centerMapOnPosition = true
                            lastPosition?.let { setView(it, 17) }
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
        configureMap()
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(pingRunnable)
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(pingRunnable)
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
                                x = it[0]
                                y = it[1]
                            }}
                    }

                    lastPosition = spatialData.position
                        .also { updatePosition(it) }
                        .also {
                            if (isRecording) {
                                recordingPath.add(it)
                                updatePath(recordingPath)
                            }
                        }
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
            centerMapOnPosition = true
            lastPosition?.let { setView(it, 20) }
            isPagerEnabled = false
            circuitListFragment?.onCircuitStarted()
        }
    }

    override suspend fun stopCircuit() {
        getFromServer("stop_circuit")
        runOnUiThread {
            centerMapOnPosition = false
            fitBounds()
            isPagerEnabled = true
            circuitListFragment?.onCircuitStopped()
        }
    }

    override suspend fun startRecording(namePath: String) {
        getFromServer("start_recording/$namePath")
        runOnUiThread {
            recordingPath.clear()
            isRecording = true
            isPagerEnabled = false
            recordFragment?.onRecordStarted()
        }
    }

    override suspend fun stopRecording() {
        isRecording = false
        getFromServer("stop_recording")
        runOnUiThread {
            updatePath("")
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
                updatePath(it)
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
                .also { it(latCount, spatialData.position.x) }
                .also { it(lonCount, spatialData.position.y) }
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

    private fun configureMap() {
        binding.webView.apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            loadUrl("file:///android_asset/leaflet.html")
        }
    }

    private fun setZoom(zoom: Int) {
        runJavascript("setZoom($zoom)")
    }


    private fun setView(point: Point, zoom: Int) {
        setView(point.toString(), zoom)
    }

    private fun setView(serializedPoint: String, zoom: Int) {
        runJavascript("setView($serializedPoint,$zoom)")
    }


    private fun updatePosition(point: Point, immediatePan: Boolean = false) {
        updatePosition(point.toString(), immediatePan)
    }

    private fun updatePosition(serializedPoint: String, immediatePan: Boolean = false) {
        runJavascript("updatePosition($serializedPoint,$centerMapOnPosition,$immediatePan)")
    }

    private fun updatePath(pointList: List<Point>) {
        updatePath("[" + pointList.joinToString(",") { it.toString() } + "]")
    }

    private fun updatePath(serializedPointList: String) {
        runJavascript("updatePath($serializedPointList)")
    }

    private fun removePath() {
        updatePath("null")
    }

    private fun fitBounds() {
        runJavascript("fitBounds()")
    }


    private fun runJavascript(command: String) {
        runOnUiThread {
            binding.webView.evaluateJavascript(command) {}
        }
    }


    class SpatialData(var yaw: Int = 0, var pitch: Int = 0, var roll: Int = 0, var position: Point = Point())

    class Point(var x: Double = 0.0, var y: Double = 0.0) {
        override fun toString(): String {
            return "[$x,$y]"
        }
    }
}