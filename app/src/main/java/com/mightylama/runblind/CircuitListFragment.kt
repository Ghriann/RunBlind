package com.mightylama.runblind

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SortedList
import com.mightylama.runblind.databinding.FragmentCircuitListBinding
import kotlinx.android.synthetic.main.fragment_record.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

/*
 * A simple [Fragment] subclass.
 */

class CircuitListFragment(private val callback: CircuitListCallback)
    : Fragment() {

    private var binding: FragmentCircuitListBinding? = null

    interface CircuitListCallback {
        suspend fun startCircuit(circuitIndex: Int)
        suspend fun stopCircuit()
        var serverState: MainActivity.ServerState
        var circuitName: String?
        var circuitIndex: Int?
    }

    private val startCircuitListener : View.OnClickListener = View.OnClickListener {
        onCircuitWaiting()
        callback.circuitIndex?.let { i ->
            binding?.let { GlobalScope.launch { callback.startCircuit(i) } }
        }
    }

    private val stopCircuitListener : View.OnClickListener = View.OnClickListener {
        onCircuitWaiting()
        GlobalScope.launch { callback.stopCircuit() }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCircuitListBinding.inflate(layoutInflater)

        binding?.button?.setOnClickListener(startCircuitListener)
        when (callback.serverState){
            MainActivity.ServerState.Connected -> onCircuitStopped()
            MainActivity.ServerState.Disconnected -> onCircuitWaiting()
            MainActivity.ServerState.Undefined -> onCircuitWaiting()
        }

        callback.circuitName?.let { updateCircuitName(it) }

        // Inflate the layout for this fragment
        return binding?.root
    }


    fun onCircuitStarted() {
        binding?.apply {
            loading.visibility = View.GONE
            button.apply {
                isClickable = true
                setImageResource(R.drawable.ic_baseline_stop)
                setOnClickListener(stopCircuitListener)
            }
        }
    }

    fun onCircuitStopped() {
        binding?.apply{
            loading.visibility = View.GONE
            button.apply {
                isClickable = true
                setImageResource(R.drawable.ic_baseline_play_arrow)
                setOnClickListener(startCircuitListener)
            }
        }
    }

    fun onCircuitWaiting() {

        binding?.apply {
            loading.visibility = View.VISIBLE
            button.apply {
                isClickable = false
                setImageResource(0)
            }
        }
    }

    fun updateCircuitName(name: String) {
        binding?.circuitName?.text = name
    }
}