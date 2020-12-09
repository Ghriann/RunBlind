package com.mightylama.runblind

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mightylama.runblind.databinding.FragmentCompassBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass.
 */

class CompassFragment(callback: CompassFragmentCallback) : Fragment() {

    private lateinit var binding: FragmentCompassBinding

    private val onCompassStartListener : View.OnClickListener = View.OnClickListener {
        onCompassWaiting()
        GlobalScope.launch { callback.startCompass() }
    }

    private val onCompassStopListener : View.OnClickListener = View.OnClickListener {
        onCompassWaiting()
        GlobalScope.launch { callback.stopCompass() }
    }

    interface CompassFragmentCallback {
        suspend fun startCompass()
        suspend fun stopCompass()
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentCompassBinding.inflate(layoutInflater)

        binding.button.setOnClickListener(onCompassStartListener)

        onCompassWaiting()

        return binding.root
    }

    fun onRecordStarted() {
        binding.apply {
            loading.visibility = View.GONE
            button.apply {
                isClickable = true
                setImageResource(R.drawable.ic_baseline_stop)
                setOnClickListener(onCompassStopListener)
            }
        }
    }

    fun onRecordStopped() {
        binding.apply {
            loading.visibility = View.GONE
            button.apply {
                isClickable = true
                setImageResource(R.drawable.ic_baseline_play_arrow)
                setOnClickListener(onCompassStartListener)
            }
        }
    }

    fun onCompassWaiting() {
        binding.apply {
            loading.visibility = View.VISIBLE
            button.apply {
                isClickable = false
                setImageResource(0)
            }
        }
    }
}