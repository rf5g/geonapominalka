package com.example.geonapominalka.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.geonapominalka.GeoApp
import com.example.geonapominalka.R
import com.example.geonapominalka.data.Reminder
import com.example.geonapominalka.databinding.ActivityTaskListBinding
import com.example.geonapominalka.util.Constants
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Менеджер задач (п.1.7 ТЗ): список активных задач + опционально выполненных. */
class TaskListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskListBinding
    private lateinit var viewModel: TaskListViewModel
    private lateinit var adapter: TaskListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val app = GeoApp.from(this)
        viewModel = ViewModelProvider(this, TaskListViewModel.Factory(app.reminderRepository))[TaskListViewModel::class.java]

        adapter = TaskListAdapter(
            onEdit = { reminder -> openEdit(reminder) },
            onDelete = { reminder -> confirmDelete(reminder) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.activeReminders.observe(this) { active ->
            val done = if (viewModel.showDone.value == true) viewModel.doneReminders.value.orEmpty() else emptyList()
            adapter.submitList(active + done)
            binding.emptyView.visibility = if (active.isEmpty() && done.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
        viewModel.doneReminders.observe(this) { /* пересчёт произойдёт через activeReminders observer при следующем апдейте */ }

        binding.toggleShowDone.setOnCheckedChangeListener { _, _ -> viewModel.toggleShowDone(); refreshList() }
        binding.fabAdd.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
    }

    private fun refreshList() {
        val active = viewModel.activeReminders.value.orEmpty()
        val done = if (viewModel.showDone.value == true) viewModel.doneReminders.value.orEmpty() else emptyList()
        adapter.submitList(active + done)
    }

    private fun openEdit(reminder: Reminder) {
        startActivity(Intent(this, TaskEditActivity::class.java).apply {
            putExtra(Constants.EXTRA_EDIT_REMINDER_ID, reminder.id)
        })
    }

    private fun confirmDelete(reminder: Reminder) {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.dialog_delete_message)
            .setPositiveButton(R.string.action_yes) { d, _ -> viewModel.delete(reminder); d.dismiss() }
            .setNegativeButton(R.string.action_no) { d, _ -> d.dismiss() }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
