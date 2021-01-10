package com.mightylama.runblind

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mightylama.runblind.databinding.FragmentRecordBinding
import kotlinx.android.synthetic.main.fragment_record.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


/**
 * A simple [Fragment] subclass.
 */
class RecordFragment(private var callback: RecordFragmentCallback) : Fragment() {

    var binding: FragmentRecordBinding? = null

    private val onRecordStartListener : View.OnClickListener = View.OnClickListener {

        var name = binding?.nameInput?.text.toString()

        if (name.isEmpty())
            Toast.makeText(context, "Please enter a name !", Toast.LENGTH_SHORT).show()

        else {
            binding?.let {
                name += if (it.closedCheckbox.isChecked)
                    "/closed"
                else
                    "/open"
            }

            onRecordWaiting()
            GlobalScope.launch { callback.startRecording(name) }
        }
    }

    private val onRecordStopListener : View.OnClickListener = View.OnClickListener {
        onRecordWaiting()
        GlobalScope.launch { callback.stopRecording() }
    }

    interface RecordFragmentCallback {
        suspend fun startRecording(namePath: String)
        suspend fun stopRecording()
        var serverState: MainActivity.ServerState
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentRecordBinding.inflate(layoutInflater)

        binding?.button?.setOnClickListener(onRecordStartListener)

        when (callback.serverState){
            MainActivity.ServerState.Connected -> onRecordStopped()
            MainActivity.ServerState.Disconnected -> onRecordWaiting()
            MainActivity.ServerState.Undefined -> onRecordWaiting()
        }

        binding?.nameInput?.apply {
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { textView, actionId, event ->
                if ((actionId == EditorInfo.IME_ACTION_DONE) || ((event?.keyCode == KeyEvent.KEYCODE_ENTER) && (event.action == KeyEvent.ACTION_DOWN))) {
                    val manager = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    manager.hideSoftInputFromWindow(textView.windowToken, 0)
                    binding?.root?.requestFocus()
                    true
                } else {
                    false
                }
            }
        }


        return binding?.root
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
        binding?.apply {
            loading.visibility = View.GONE
            nameInput.apply {
                isEnabled = true
                if (text.isNotEmpty()) activity?.runOnUiThread {
                    Toast.makeText(activity, "$text registered!", Toast.LENGTH_SHORT).show()
                    setText("")
                }
            }
            closedCheckbox.isEnabled = true
            button.apply {
                isClickable = true
                setImageResource(R.drawable.ic_baseline_play_arrow)
                setOnClickListener(onRecordStartListener)
            }
        }
    }

    fun onRecordWaiting() {
        binding?.apply {
            loading.visibility = View.VISIBLE
            nameInput.isEnabled = false
            closedCheckbox.isEnabled = false
            button.apply {
                isClickable = false
                setImageResource(0)
            }
        }
    }
}