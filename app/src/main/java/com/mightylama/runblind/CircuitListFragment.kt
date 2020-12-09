package com.mightylama.runblind

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class CircuitListFragment(val callback: CircuitListCallback, private val circuitList : ArrayList<String>)
    : Fragment() {

    private lateinit var binding: FragmentCircuitListBinding
    private lateinit var adapter: ArrayAdapter<String>

    interface CircuitListCallback {
        suspend fun startCircuit(circuitIndex: Int)
        suspend fun stopCircuit()
    }

    private val startCircuitListener : View.OnClickListener = View.OnClickListener {
        onCircuitWaiting()
        GlobalScope.launch { callback.startCircuit(binding.spinner.selectedItemPosition) }
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
            binding.spinner.adapter = adapter
        }

        binding.button.setOnClickListener(startCircuitListener)

        // Inflate the layout for this fragment
        return binding.root
    }

    fun notifyDataChanged() {
        adapter.notifyDataSetChanged()
    }

    fun onCircuitStarted() {
        binding.apply {
            loading.visibility = View.GONE
            button.apply {
                isClickable = true
                setImageResource(R.drawable.ic_baseline_stop)
                setOnClickListener(stopCircuitListener)
            }
        }
    }

    fun onCircuitStopped() {
        binding.apply{
            spinner.isClickable = true
            loading.visibility = View.GONE
            button.apply {
                isClickable = true
                setImageResource(R.drawable.ic_baseline_play_arrow)
                setOnClickListener(startCircuitListener)
            }
        }
    }

    fun onCircuitWaiting() {

        binding.apply {
            spinner.isClickable = false
            loading.visibility = View.VISIBLE
            binding.button.apply {
                isClickable = false
                setImageResource(0)
            }
        }
    }
}