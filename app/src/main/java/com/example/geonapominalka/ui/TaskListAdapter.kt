package com.example.geonapominalka.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.geonapominalka.data.Reminder
import com.example.geonapominalka.databinding.ItemTaskBinding
import java.util.Locale

class TaskListAdapter(
    private val onEdit: (Reminder) -> Unit,
    private val onDelete: (Reminder) -> Unit
) : ListAdapter<Reminder, TaskListAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(reminder: Reminder) {
            binding.taskName.text = reminder.name
            binding.taskCoordinates.text = String.format(
                Locale.getDefault(), "%.5f, %.5f · %dм", reminder.latitude, reminder.longitude, reminder.radius
            )
            binding.root.setOnClickListener { onEdit(reminder) }
            binding.btnEdit.setOnClickListener { onEdit(reminder) }
            binding.btnDelete.setOnClickListener { onDelete(reminder) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Reminder>() {
            override fun areItemsTheSame(oldItem: Reminder, newItem: Reminder) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Reminder, newItem: Reminder) = oldItem == newItem
        }
    }
}
