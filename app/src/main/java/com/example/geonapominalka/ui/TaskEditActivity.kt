package com.example.geonapominalka.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.geonapominalka.GeoApp
import com.example.geonapominalka.R
import com.example.geonapominalka.databinding.ActivityTaskEditBinding
import com.example.geonapominalka.util.Constants
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * Экран создания/редактирования напоминания (п.1.2, 1.3 ТЗ).
 * Координаты доступны только для чтения; их можно поменять только
 * через кнопку "Выбрать на карте" (MapPickerActivity).
 */
class TaskEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskEditBinding
    private lateinit var viewModel: TaskEditViewModel
    private var editingId: Long = 0L

    private val pickOnMap = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        val lat = data.getDoubleExtra(Constants.EXTRA_PICKED_LAT, Double.NaN)
        val lng = data.getDoubleExtra(Constants.EXTRA_PICKED_LNG, Double.NaN)
        if (!lat.isNaN() && !lng.isNaN()) {
            viewModel.updateCoordinates(lat, lng)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val app = GeoApp.from(this)
        viewModel = ViewModelProvider(this, TaskEditViewModel.Factory(app.reminderRepository))[TaskEditViewModel::class.java]

        editingId = intent.getLongExtra(Constants.EXTRA_EDIT_REMINDER_ID, 0L)
        if (editingId != 0L) {
            title = getString(R.string.title_task_edit)
            viewModel.load(editingId)
            binding.btnDelete.visibility = android.view.View.VISIBLE
        } else {
            title = getString(R.string.title_task_create)
            val lat = intent.getDoubleExtra(Constants.EXTRA_INITIAL_LAT, 0.0)
            val lng = intent.getDoubleExtra(Constants.EXTRA_INITIAL_LNG, 0.0)
            viewModel.setInitialCoordinates(lat, lng)
        }

        viewModel.reminder.observe(this) { reminder ->
            reminder ?: return@observe
            binding.coordinatesText.text = getString(
                R.string.coordinates_format, reminder.latitude, reminder.longitude
            )
            if (binding.nameField.text.isNullOrEmpty() && reminder.name.isNotEmpty()) {
                binding.nameField.setText(reminder.name)
            }
            if (binding.descriptionField.text.isNullOrEmpty() && !reminder.description.isNullOrEmpty()) {
                binding.descriptionField.setText(reminder.description)
            }
            if (binding.radiusField.text.isNullOrEmpty()) {
                binding.radiusField.setText(reminder.radius.toString())
            }
        }

        viewModel.saved.observe(this) { saved -> if (saved) finish() }

        binding.btnPickOnMap.setOnClickListener {
            val current = viewModel.reminder.value
            val intent = Intent(this, MapPickerActivity::class.java).apply {
                if (current != null) {
                    putExtra(Constants.EXTRA_INITIAL_LAT, current.latitude)
                    putExtra(Constants.EXTRA_INITIAL_LNG, current.longitude)
                }
            }
            pickOnMap.launch(intent)
        }

        binding.btnSave.setOnClickListener { onSaveClicked() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
    }

    private fun onSaveClicked() {
        val name = binding.nameField.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.error_name_required)
            return
        }
        binding.nameLayout.error = null
        val description = binding.descriptionField.text?.toString()?.trim()
        val radius = binding.radiusField.text?.toString()?.toIntOrNull() ?: 200
        viewModel.save(name, description, radius)
    }

    private fun confirmDelete() {
        val name = binding.nameField.text?.toString()?.trim()?.ifBlank { null }
            ?: viewModel.reminder.value?.name.orEmpty()
        val description = binding.descriptionField.text?.toString()?.trim()
            ?: viewModel.reminder.value?.description
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_message)
            .setMessage(buildDeleteConfirmationMessage(name, description))
            .setPositiveButton(R.string.action_yes) { d, _ -> viewModel.delete(); d.dismiss() }
            .setNegativeButton(R.string.action_no) { d, _ -> d.dismiss() }
            .show()
    }

    private fun buildDeleteConfirmationMessage(name: String, description: String?): String = buildString {
        append(getString(R.string.dialog_delete_task_name_line, name))
        if (!description.isNullOrBlank()) {
            append("\n")
            append(getString(R.string.dialog_delete_task_description_line, description))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
