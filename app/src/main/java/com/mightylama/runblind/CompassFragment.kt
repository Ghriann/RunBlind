package com.mightylama.runblind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mightylama.runblind.databinding.FragmentCompassBinding
import kotlinx.android.synthetic.main.dialog_scanner.view.*
import kotlin.math.PI
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass.
 */

class CompassFragment(private val callback: CompassFragmentCallback) : Fragment() {

    private var binding: FragmentCompassBinding? = null

    private val onCompassStartListener : View.OnClickListener = View.OnClickListener {
        onCompassWaiting()
        binding?.let { GlobalScope.launch { callback.startCompass() }}
    }

    private val onCompassStopListener : View.OnClickListener = View.OnClickListener {
        onCompassWaiting()
        GlobalScope.launch { callback.stopCompass() }
    }

    interface CompassFragmentCallback {
        suspend fun startCompass()
        suspend fun stopCompass()
        var serverState: MainActivity.ServerState
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentCompassBinding.inflate(layoutInflater)

        binding?.button?.setOnClickListener(onCompassStartListener)

        when (callback.serverState){
            MainActivity.ServerState.Connected -> onCompassStopped()
            MainActivity.ServerState.Disconnected -> onCompassWaiting()
            MainActivity.ServerState.Undefined -> onCompassWaiting()
        }

        return binding?.root
    }

    fun onCompassStarted() {
        binding?.apply {
            loading.visibility = View.GONE
            button.apply {
                isClickable = true
                setImageResource(R.drawable.ic_baseline_stop)
                setOnClickListener(onCompassStopListener)
            }
        }
    }

    fun onCompassStopped() {
        binding?.apply {
            loading.visibility = View.GONE
            button.apply {
                isClickable = true
                setImageResource(R.drawable.ic_baseline_play_arrow)
                setOnClickListener(onCompassStartListener)
            }
        }
    }

    fun onCompassWaiting() {
        binding?.apply {
            loading.visibility = View.VISIBLE
            button.apply {
                isClickable = false
                setImageResource(0)
            }
        }
    }

    fun displayOrientationHint(degree: Int) {
        binding?.orientationHintText?.apply {
            visibility = View.VISIBLE
            text = "Setting orientation to : $degree°"
        }
    }

    fun hideOrientationHint() {
        binding?.orientationHintText?.apply {
            visibility = View.GONE
        }
    }
}