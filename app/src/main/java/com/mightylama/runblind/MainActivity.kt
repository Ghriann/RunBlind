package com.mightylama.runblind

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewClient
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import com.mightylama.runblind.databinding.ActivityMainBinding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.network.sockets.ConnectTimeoutException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Exception
import java.lang.ref.WeakReference

class MainActivity
    : FragmentActivity(),
    CircuitListFragment.CircuitListCallback, RecordFragment.RecordFragmentCallback, CompassFragment.CompassFragmentCallback {

    private val KEY_IP = "key_ip"

    private lateinit var binding: ActivityMainBinding
    private val circuitList = ArrayList<String>()
    private var circuitListFragment: CircuitListFragment? = null
    private var recordFragment: RecordFragment? = null
    private var compassFragment: CompassFragment? = null

    private var serializedCircuitPath: String? = null

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
                        0 -> serializedCircuitPath?.let{ updatePath(it,true) }
                        else -> removePath()
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

                response.split(",")
                    .also { updateOrientation(it.subList(0, 5)) }
                    //TODO .also { it.last() ... }
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
        val serializedList = getFromServer("get_circuit_list")
        serializedList?.let {
            runOnUiThread { updateCircuitList(it) }
        }
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
            isPagerEnabled = false
            circuitListFragment?.onCircuitStarted()
        }
    }

    override suspend fun stopCircuit() {
        getFromServer("stop_circuit")
        runOnUiThread {
            isPagerEnabled = true
            circuitListFragment?.onCircuitStopped()
        }
    }

    override suspend fun startRecording(namePath: String) {
        getFromServer("start_recording/$namePath")
        runOnUiThread {
            isPagerEnabled = false
            recordFragment?.onRecordStarted()
        }
    }

    override suspend fun stopRecording() {
        getFromServer("stop_recording")
        runOnUiThread {
            isPagerEnabled = true
            recordFragment?.onRecordStopped()
        }
    }

    override suspend fun getCircuitPath(index: Int) {
        val response = getFromServer("get_circuit_path/$index")
        response?.let {
            serializedCircuitPath = it
            runOnUiThread { updatePath(it, true) }
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


    private fun updateOrientation(spatialDataList: List<String>) {

        binding.apply {
            yawCount.text = spatialDataList[0]
            pitchCount.text = spatialDataList[1]
            rollCount.text = spatialDataList[2]
            val lat = spatialDataList[3].also { latCount.text = it }
            val lon = spatialDataList[4].also { lonCount.text = it }

            updatePosition("[$lat,$lon]")
        }
    }

    private fun updateOrientation(serializedSpatialData: String) {
         updateOrientation(serializedSpatialData.split(","))
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


    private fun updatePosition(point: Array<Double>) {
        updatePosition(serializePoint(point))
    }

    private fun updatePosition(serializedPoint: String) {
        runJavascript("updatePosition($serializedPoint)")
    }

    private fun updatePath(pointList: List<Array<Double>>, instantPan: Boolean) {
        updatePath(serializePointList(pointList), instantPan)
    }

    private fun updatePath(serializedPointList: String, instantPan: Boolean) {
        runJavascript("updatePath($serializedPointList,$instantPan)")
    }

    private fun removePath() {
        updatePath("null",true)
    }

    private fun tryFitBounds() {
        runJavascript("tryFitBounds()")
    }


    private fun runJavascript(command: String) {
        runOnUiThread {
            binding.webView.evaluateJavascript(command) {}
        }
    }

    private fun serializePoint(point: Array<Double>): String {
        return "[" + point[0] + "," + point[1] + "]"
    }

    private fun serializePointList(pointList: List<Array<Double>>): String {
        var serialized = "["
        pointList.forEach {
            serialized += "[" + it[0] + "," + it[1] + "],"
        }
        return serialized.dropLast(1) + "]"
    }
}