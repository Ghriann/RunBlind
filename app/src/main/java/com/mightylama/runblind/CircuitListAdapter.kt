package com.mightylama.runblind

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import com.mightylama.runblind.databinding.CircuitListHolderBinding

class CircuitListAdapter(private val nameList : List<String>):
    RecyclerView.Adapter<CircuitListAdapter.ViewHolder>() {

    class ViewHolder(val binding : CircuitListHolderBinding): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CircuitListHolderBinding.inflate(LayoutInflater.from(parent.context))
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.title.text = nameList[position]
    }

    override fun getItemCount(): Int {
        return nameList.size
    }
}