package com.mightylama.runblind

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class MainActivity
    : FragmentActivity(), CircuitListFragment.CircuitListCallback {

    private val KEY_IP = "key_ip"

    private lateinit var binding: ActivityMainBinding
    private val circuitList = ArrayList<String>()
    private var circuitListFragment: CircuitListFragment? = null

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

                updateOrientation(response)
            }
        }

        catch (exception: Exception) {
            runOnUiThread {
                if (serverState != ServerState.Disconnected)
                    onServerDisconnected()

                when (exception) {
                    is UnresolvedAddressException -> Toast.makeText(this, "Please enter a valid IP", Toast.LENGTH_SHORT).show()
                    is ConnectTimeoutException -> Toast.makeText(this, "Server timeout", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onServerDisconnected()
    {
        serverState = ServerState.Disconnected
        binding.dot.imageTintList = getColorStateList(R.color.dotColorDisconnected)
        circuitListFragment?.onCircuitWaiting()
    }

    private fun onServerConnected()
    {
        serverState = ServerState.Connected
        binding.dot.imageTintList = getColorStateList(R.color.dotColorConnected)
        circuitListFragment?.onCircuitStopped()

        GlobalScope.launch { getCircuitList() }
    }

    override suspend fun startCircuit(circuitIndex: Int) {
        try {
            httpClient.get<String>(baseUrl + "start_circuit/" + circuitIndex)
            runOnUiThread { circuitListFragment?.onCircuitStarted() }
        }
        catch (exception: Exception) {
            runOnUiThread { Toast.makeText(this, exception.toString(), Toast.LENGTH_SHORT).show() }
        }
    }

    override suspend fun stopCircuit() {
        try {
            httpClient.get<String>(baseUrl + "stop_circuit")
            runOnUiThread { circuitListFragment?.onCircuitStopped() }
        }
        catch (exception: Exception) {
            runOnUiThread { Toast.makeText(this, exception.toString(), Toast.LENGTH_SHORT).show() }
        }
    }

    class MainFragmentStateAdapter(var mainActivity: MainActivity): FragmentStateAdapter(mainActivity) {
        override fun getItemCount(): Int {
            return 3
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> CircuitListFragment(mainActivity, mainActivity.circuitList).also { mainActivity.circuitListFragment = it }
                1 -> RecordFragment()
                2 -> CompassFragment()
                else -> Fragment()
            }
        }
    }

    private fun updateOrientation(serializedSpatialData: String) {

        val spatialDataList = serializedSpatialData.split(",")
        if (spatialDataList.size == 6)
            binding.apply {
                yawCount.text = spatialDataList[0]
                pitchCount.text = spatialDataList[1]
                rollCount.text = spatialDataList[2]
                latCount.text = spatialDataList[3]
                lonCount.text = spatialDataList[4]
                heightCount.text = spatialDataList[5]
            }
    }

    private suspend fun getCircuitList() {
        try {
            val response = httpClient.get<String>(baseUrl + "get_circuit_list")
            runOnUiThread { updateCircuitList(response) }
        }
        catch (exception: Exception) {
            Toast.makeText(this, exception.toString(), Toast.LENGTH_SHORT).show()
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