package com.mightylama.runblind

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
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

    private var baseUrl: String? = null
    private val httpClient = HttpClient()
    private var serverState = ServerState.Undefined

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

        binding.pager.adapter = MainFragmentStateAdapter(this)
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
                    .also { updateOrientation(it.subList(0, 6)) }
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

        GlobalScope.launch {
            getVolume()
            getCircuitList()
        }
    }


    private suspend fun getCircuitList() {
        val serializedList = getFromServer("get_circuit_list")
        runOnUiThread { updateCircuitList(serializedList) }
    }

    private suspend fun getVolume(){
        val volume = getFromServer("get_volume").toInt()
        runOnUiThread { updateVolume(volume) }
    }

    private suspend fun setVolume(volume: Int){
        getFromServer("set_volume/$volume")
    }

    override suspend fun startCircuit(circuitIndex: Int) {
        getFromServer("start_circuit/$circuitIndex")
        runOnUiThread { circuitListFragment?.onCircuitStarted() }
    }

    override suspend fun stopCircuit() {
        getFromServer("stop_circuit")
        runOnUiThread { circuitListFragment?.onCircuitStopped() }
    }

    override suspend fun startRecording(namePath: String) {
        getFromServer("start_recording/$namePath")
        runOnUiThread { recordFragment?.onRecordStarted() }
    }

    override suspend fun stopRecording() {
        getFromServer("stop_recording")
        runOnUiThread { recordFragment?.onRecordStopped() }
    }

    override suspend fun startCompass() {
        getFromServer("start_compass")
        runOnUiThread { compassFragment?.onRecordStarted() }
    }

    override suspend fun stopCompass() {
        getFromServer("stop_compass")
        runOnUiThread { compassFragment?.onRecordStopped() }
    }


    private suspend fun getFromServer(path: String) : String{
        return try {
            httpClient.get(baseUrl + path)
        } catch (exception: Exception) {
            runOnUiThread { Toast.makeText(this, exception.toString(), Toast.LENGTH_SHORT).show() }
            ""
        }
    }


    private fun updateOrientation(spatialDataList: List<String>) {
        binding.apply {
            yawCount.text = spatialDataList[0]
            pitchCount.text = spatialDataList[1]
            rollCount.text = spatialDataList[2]
            latCount.text = spatialDataList[3]
            lonCount.text = spatialDataList[4]
            heightCount.text = spatialDataList[5]
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

    private enum class ServerState{
        Connected, Disconnected, Undefined
    }
}