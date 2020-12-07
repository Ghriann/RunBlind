package com.mightylama.runblind

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mightylama.runblind.databinding.FragmentCompassBinding

/**
 * A simple [Fragment] subclass.
 */

class CompassFragment : Fragment() {

    private lateinit var binding: FragmentCompassBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentCompassBinding.inflate(layoutInflater)
        return binding.root
    }
}