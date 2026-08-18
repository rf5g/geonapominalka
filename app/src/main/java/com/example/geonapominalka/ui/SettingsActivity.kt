package com.example.geonapominalka.ui

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.geonapominalka.GeoApp
import com.example.geonapominalka.R
import com.example.geonapominalka.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Настройки приложения (п.1.7 ТЗ): тема, звук, вибрация, частота опроса, сброс данных. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var app: GeoApp

    private val pickRingtone = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            lifecycleScope.launch { app.settingsRepository.setSoundUri(uri?.toString()) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        app = GeoApp.from(this)

        setupTheme()
        setupInterval()
        setupVibration()
        setupSound()
        setupReset()
    }

    private fun setupTheme() {
        lifecycleScope.launch {
            app.settingsRepository.theme.collect { theme ->
                val id = when (theme) {
                    "light" -> R.id.radioThemeLight
                    "dark" -> R.id.radioThemeDark
                    else -> R.id.radioThemeSystem
                }
                if (binding.themeGroup.checkedRadioButtonId != id) binding.themeGroup.check(id)
            }
        }
        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radioThemeLight -> "light"
                R.id.radioThemeDark -> "dark"
                else -> "system"
            }
            lifecycleScope.launch { app.settingsRepository.setTheme(value) }
        }
    }

    private fun setupInterval() {
        lifecycleScope.launch {
            app.settingsRepository.intervalSeconds.collect { seconds ->
                val id = when (seconds) {
                    30 -> R.id.radioInterval30
                    300 -> R.id.radioInterval300
                    else -> R.id.radioInterval60
                }
                if (binding.intervalGroup.checkedRadioButtonId != id) binding.intervalGroup.check(id)
            }
        }
        binding.intervalGroup.setOnCheckedChangeListener { _, checkedId ->
            val seconds = when (checkedId) {
                R.id.radioInterval30 -> 30
                R.id.radioInterval300 -> 300
                else -> 60
            }
            lifecycleScope.launch { app.settingsRepository.setIntervalSeconds(seconds) }
        }
    }

    private fun setupVibration() {
        lifecycleScope.launch {
            app.settingsRepository.vibration.collect { enabled ->
                if (binding.switchVibration.isChecked != enabled) binding.switchVibration.isChecked = enabled
            }
        }
        binding.switchVibration.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { app.settingsRepository.setVibration(checked) }
        }
    }

    private fun setupSound() {
        binding.btnChooseSound.setOnClickListener {
            val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
            pickRingtone.launch(intent)
        }
    }

    private fun setupReset() {
        binding.btnResetData.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.dialog_reset_message)
                .setPositiveButton(R.string.action_yes) { d, _ ->
                    lifecycleScope.launch { app.reminderRepository.resetAll() }
                    d.dismiss()
                }
                .setNegativeButton(R.string.action_no) { d, _ -> d.dismiss() }
                .show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
