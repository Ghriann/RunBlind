package com.mightylama.runblind

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mightylama.runblind.databinding.FragmentRecordBinding
import kotlinx.android.synthetic.main.fragment_record.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


/**
 * A simple [Fragment] subclass.
 */
class RecordFragment(private var callback: RecordFragmentCallback) : Fragment() {

    private lateinit var binding: FragmentRecordBinding

    private val onRecordStartListener : View.OnClickListener = View.OnClickListener {
        onRecordWaiting()
        GlobalScope.launch { callback.startRecording(
            with (binding) {
                var namePath = nameInput.text.toString()
                when (radioGroup.checkedRadioButtonId) {
                    radioOpen.id -> namePath += "/open"
                    radioClosed.id -> namePath += "/closed"
                }
                namePath
            }
        ) }
    }

    private val onRecordStopListener : View.OnClickListener = View.OnClickListener {
        onRecordWaiting()
        GlobalScope.launch { callback.stopRecording() }
    }

    interface RecordFragmentCallback {
        suspend fun startRecording(namePath: String)
        suspend fun stopRecording()
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentRecordBinding.inflate(layoutInflater)

        binding.button.setOnClickListener(onRecordStartListener)

        onRecordWaiting()

        return binding.root
    }

    fun onRecordStarted() {
        binding.apply {
            loading.visibility = View.GONE
            button.apply {
                isClickable = true
                setImageResource(R.drawable.ic_baseline_stop)
                setOnClickListener(onRecordStopListener)
            }
        }
    }

    fun onRecordStopped() {
        binding.apply {
            loading.visibility = View.GONE
            nameInput.isEnabled = true
            radioOpen.isEnabled = true
            radioClosed.isEnabled = true
            button.apply {
                isClickable = true
                setImageResource(R.drawable.ic_baseline_play_arrow)
                setOnClickListener(onRecordStartListener)
            }
        }
    }

    fun onRecordWaiting() {
        binding.apply {
            loading.visibility = View.VISIBLE
            nameInput.isEnabled = false
            radioOpen.isEnabled = false
            radioClosed.isEnabled = false
            button.apply {
                isClickable = false
                setImageResource(0)
            }
        }
    }
}