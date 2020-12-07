package com.mightylama.runblind

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.mightylama.runblind.databinding.ActivityMainBinding
import java.util.stream.Collectors.toList

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val circuitList = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pager.adapter = MainFragmentStateAdapter(this, circuitList)
        TabLayoutMediator(binding.tab, binding.pager) { tab, position ->
            when(position) {
                0 -> tab.text = "Guiding"
                1 -> tab.text = "Recording"
                2 -> tab.text = "Compass"
            }
        }.attach()

        populateDummyCircuits()
    }

    private fun populateDummyCircuits() {
        circuitList.addAll(listOf("Promenade", "Cours cours", "Traversée", "Patrouille", "Natation synchro", "Aventure", "Petite  balade", "Foot"))
    }

    class MainFragmentStateAdapter(fragmentActivity: FragmentActivity, private val circuitList: ArrayList<String>): FragmentStateAdapter(fragmentActivity) {
        override fun getItemCount(): Int {
            return 3
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> CircuitListFragment(circuitList)
                1 -> RecordFragment()
                2 -> CompassFragment()
                else -> Fragment()
            }
        }
    }
}