package com.example.geonapominalka.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.geonapominalka.GeoApp
import com.example.geonapominalka.R
import com.example.geonapominalka.databinding.ActivitySettingsBinding
import com.example.geonapominalka.util.TileSources
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

    // Повторный запрос ACTIVITY_RECOGNITION при включении адаптивного режима, если разрешение не выдано
    private val requestActivityRecognition = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        lifecycleScope.launch { app.settingsRepository.setAdaptiveMode(true) }
        if (!granted) {
            android.widget.Toast.makeText(
                this, R.string.msg_activity_recognition_denied, android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        app = GeoApp.from(this)

        setupTheme()
        setupAdaptiveModeAndInterval()
        setupInterval()
        setupMapStyle()
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

    /**
     * Адаптивный режим — главнее ручного интервала: список фиксированных значений
     * остаётся видимым, но дизейблится, пока адаптивный режим включён (п.6 обсуждения).
     */
    private fun setupAdaptiveModeAndInterval() {
        lifecycleScope.launch {
            app.settingsRepository.adaptiveMode.collect { enabled ->
                if (binding.switchAdaptiveMode.isChecked != enabled) binding.switchAdaptiveMode.isChecked = enabled
                setIntervalGroupEnabled(!enabled)
            }
        }
        binding.switchAdaptiveMode.setOnCheckedChangeListener { _, checked ->
            if (checked && !hasActivityRecognitionPermission()) {
                // Разрешение не выдано — объясняем ещё раз, зачем оно нужно, и запрашиваем повторно.
                // Сама настройка включится в колбэке запроса разрешения (независимо от результата —
                // адаптивный режим и без него работает, просто менее точно, по скорости из GPS).
                binding.switchAdaptiveMode.isChecked = false
                showActivityRecognitionRationale()
            } else {
                lifecycleScope.launch { app.settingsRepository.setAdaptiveMode(checked) }
            }
        }
    }

    private fun showActivityRecognitionRationale() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_activity_recognition_title)
            .setMessage(R.string.dialog_activity_recognition_message)
            .setPositiveButton(R.string.action_continue) { dialog, _ ->
                dialog.dismiss()
                requestActivityRecognition.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun hasActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    private fun setIntervalGroupEnabled(enabled: Boolean) {
        binding.intervalGroup.alpha = if (enabled) 1.0f else 0.4f
        for (i in 0 until binding.intervalGroup.childCount) {
            binding.intervalGroup.getChildAt(i).isEnabled = enabled
        }
    }

    private fun setupInterval() {
        lifecycleScope.launch {
            app.settingsRepository.intervalSeconds.collect { seconds ->
                val id = when (seconds) {
                    30 -> R.id.radioInterval30
                    180 -> R.id.radioInterval180
                    300 -> R.id.radioInterval300
                    600 -> R.id.radioInterval600
                    1200 -> R.id.radioInterval1200
                    else -> R.id.radioInterval60
                }
                if (binding.intervalGroup.checkedRadioButtonId != id) binding.intervalGroup.check(id)
            }
        }
        binding.intervalGroup.setOnCheckedChangeListener { _, checkedId ->
            val seconds = when (checkedId) {
                R.id.radioInterval30 -> 30
                R.id.radioInterval180 -> 180
                R.id.radioInterval300 -> 300
                R.id.radioInterval600 -> 600
                R.id.radioInterval1200 -> 1200
                else -> 60
            }
            lifecycleScope.launch { app.settingsRepository.setIntervalSeconds(seconds) }
        }
    }

    /**
     * Стиль карты (п.1.7 ТЗ, "выбор провайдера карт" — реализовано как выбор стиля/слоя,
     * т.к. у бесплатных провайдеров без ключа нет отдельного переключателя "провайдер").
     * Значение хранится тем же ключом mapType, что и переключатель на самой карте —
     * они синхронизированы через один и тот же Flow в SettingsRepository.
     */
    private fun setupMapStyle() {
        val idByStyle = mapOf(
            TileSources.MapStyle.LIGHT to R.id.radioStyleLight,
            TileSources.MapStyle.VOYAGER to R.id.radioStyleVoyager,
            TileSources.MapStyle.SATELLITE to R.id.radioStyleSatellite,
            TileSources.MapStyle.HYBRID to R.id.radioStyleHybrid
        )
        val styleById = idByStyle.entries.associate { (style, id) -> id to style }

        lifecycleScope.launch {
            app.settingsRepository.mapType.collect { index ->
                val id = idByStyle.getValue(TileSources.MapStyle.fromIndex(index))
                if (binding.mapStyleGroup.checkedRadioButtonId != id) binding.mapStyleGroup.check(id)
            }
        }
        binding.mapStyleGroup.setOnCheckedChangeListener { _, checkedId ->
            val style = styleById[checkedId] ?: return@setOnCheckedChangeListener
            lifecycleScope.launch { app.settingsRepository.setMapType(style.ordinal) }
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
