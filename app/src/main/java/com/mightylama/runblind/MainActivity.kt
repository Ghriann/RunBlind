package com.mightylama.runblind

import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import com.mightylama.runblind.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.stream.Collectors.toList

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val circuitList = ArrayList<String>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pingRunnable = object : Runnable {
        override fun run() {
            runBlocking {
                launch { getOrientation() }
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    var a = 1
    var b = 2
    var c = 3

    private lateinit var ip: String

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


    private suspend fun getOrientation()
    {
        delay(50)
        updateOrientation(a.toFloat(), b.toFloat(), c.toFloat(), a.toFloat(), b.toFloat(), c.toFloat())
        a++
        b++
        c++
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
        var editText = TextInputEditText(this)
        AlertDialog.Builder(this)
            .setMessage("Please enter server IP")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("OK") { _: DialogInterface, _: Int ->
                Toast.makeText(this, editText.text, Toast.LENGTH_SHORT).show()
            }
            .create()
            .show()
    }
}