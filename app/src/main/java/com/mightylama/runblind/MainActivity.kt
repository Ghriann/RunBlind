package com.mightylama.runblind

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
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
import io.ktor.client.features.ConnectTimeoutException
import io.ktor.client.features.get
import io.ktor.client.request.get
import io.ktor.network.sockets.ConnectTimeoutException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.Exception
import java.net.ConnectException
import java.util.stream.Collectors.toList

class MainActivity : FragmentActivity() {

    public val KEY_IP = "key_ip"

    private lateinit var binding: ActivityMainBinding
    private val circuitList = ArrayList<String>()

    private var baseUrl: String? = null
    private val httpClient = HttpClient()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pingRunnable = object : Runnable {
        override fun run() {
            baseUrl?.let {
                GlobalScope.launch {
                    getSpatialData()
                }
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

        populateDummyCircuits()
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
        circuitList.addAll(listOf("Circuit 1", "Circuit 2", "Circuit 3"))
    }


    private suspend fun getSpatialData()
    {
        try {
            val response = httpClient.get<String>(baseUrl + "get_spatial_data")

            runOnUiThread {
                onServerConnected()
                Toast.makeText(this, response, Toast.LENGTH_SHORT).show()
            }
        }

        catch (exception: Exception) {
            onServerDisconnected()
            when (exception) {
                is UnresolvedAddressException -> runOnUiThread { Toast.makeText(this, "Please enter a valid IP", Toast.LENGTH_SHORT).show() }
                is ConnectTimeoutException ->  runOnUiThread { Toast.makeText(this, "Server timeout", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun onServerDisconnected()
    {
        binding.dot.imageTintList = getColorStateList(R.color.dotColorDisconnected)
    }

    private fun onServerConnected()
    {
        binding.dot.imageTintList = getColorStateList(R.color.dotColorConnected)
    }



    class MainFragmentStateAdapter(var mainActivity: MainActivity): FragmentStateAdapter(mainActivity) {
        override fun getItemCount(): Int {
            return 3
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> CircuitListFragment(mainActivity.circuitList)
                1 -> RecordFragment()
                2 -> CompassFragment()
                else -> Fragment()
            }
        }
    }

    private fun updateOrientation(yaw: Float, pitch: Float, roll: Float, lat: Float, lon: Float, height: Float) {
        binding.apply {
            yawCount.text = yaw.toString()
            pitchCount.text = pitch.toString()
            rollCount.text = roll.toString()
            latCount.text = lat.toString()
            lonCount.text = lon.toString()
            heightCount.text = height.toString()
        }
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
}