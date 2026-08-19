package com.example.geonapominalka.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.geonapominalka.databinding.ItemLogEntryBinding
import com.example.geonapominalka.util.AppLogger
import java.text.SimpleDateFormat
import java.util.Locale

class LogAdapter : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    private var items: List<AppLogger.Entry> = emptyList()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun submitList(newItems: List<AppLogger.Entry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLogEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        holder.binding.logTime.text = timeFormat.format(entry.timestamp)
        holder.binding.logTag.text = entry.tag
        holder.binding.logMessage.text = entry.message
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val binding: ItemLogEntryBinding) : RecyclerView.ViewHolder(binding.root)
}
