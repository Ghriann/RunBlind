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
import com.mightylama.runblind.databinding.CircuitListHolderBinding
import com.mightylama.runblind.databinding.FragmentCircuitListBinding
import kotlinx.android.synthetic.main.fragment_record.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

/*
 * A simple [Fragment] subclass.
 */

class CircuitListFragment(private val callback: CircuitListCallback, private val circuitList : ArrayList<String>)
    : Fragment() {

    var binding: FragmentCircuitListBinding? = null
    private lateinit var adapter: ArrayAdapter<String>

    interface CircuitListCallback {
        suspend fun startCircuit(circuitIndex: Int)
        suspend fun stopCircuit()
        suspend fun getCircuitPath(index: Int)
        var serverState: MainActivity.ServerState
    }

    private val startCircuitListener : View.OnClickListener = View.OnClickListener {
        onCircuitWaiting()
        binding?.let { GlobalScope.launch { callback.startCircuit(it.spinner.selectedItemPosition) } }
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

        context?.let {
            adapter = ArrayAdapter<String>(it, android.R.layout.simple_spinner_item, circuitList)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            binding?.spinner?.adapter = adapter
        }
        binding?.spinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {}

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                GlobalScope.launch { callback.getCircuitPath(p2) }
            }
        }

        binding?.button?.setOnClickListener(startCircuitListener)
        when (callback.serverState){
            MainActivity.ServerState.Connected -> onCircuitStopped()
            MainActivity.ServerState.Disconnected -> onCircuitWaiting()
            MainActivity.ServerState.Undefined -> onCircuitWaiting()
        }

        // Inflate the layout for this fragment
        return binding?.root
    }

    fun notifyDataChanged() {
        adapter.notifyDataSetChanged()
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
            spinner.isEnabled = true
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
            spinner.isEnabled = false
            loading.visibility = View.VISIBLE
            button.apply {
                isClickable = false
                setImageResource(0)
            }
        }
    }
}