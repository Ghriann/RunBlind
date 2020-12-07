package com.mightylama.runblind

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SortedList
import com.mightylama.runblind.databinding.CircuitListHolderBinding
import com.mightylama.runblind.databinding.FragmentCircuitListBinding

// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

/*
 * A simple [Fragment] subclass.
 */

class CircuitListFragment(private val circuitList : ArrayList<String>) : Fragment() {

    private lateinit var binding: FragmentCircuitListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCircuitListBinding.inflate(layoutInflater)

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = CircuitListAdapter(circuitList)
        }

        // Inflate the layout for this fragment
        return binding.root
    }
}