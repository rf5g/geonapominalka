package com.example.geonapominalka

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import com.example.geonapominalka.data.AppDatabase
import com.example.geonapominalka.data.ReminderRepository
import com.example.geonapominalka.data.SettingsRepository
import com.example.geonapominalka.service.LocationForegroundService
import com.example.geonapominalka.util.Constants
import com.example.geonapominalka.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GeoApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val reminderRepository by lazy { ReminderRepository(database.reminderDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        configureOsmdroid()
        createNotificationChannel()
        applySavedTheme()
        observeActiveTaskCount()
    }

    /**
     * OSMDroid требует явный User-Agent (иначе публичные тайл-сервера OSM банят запросы)
     * и путь для кэша тайлов. Ключ API не нужен — сервис полностью бесплатный.
     *
     * ВАЖНО: Configuration.getInstance() — синглтон на весь процесс, настраивается
     * здесь и ТОЛЬКО здесь, один раз при старте приложения (до создания любой Activity).
     * Повторный вызов .load(...) в каком-либо экране опасен: он перечитывает настройки
     * из SharedPreferences и может откатить путь кэша обратно на дефолтный — тогда каждое
     * пересоздание экрана (поворот экрана, возврат из другой Activity и т.д.) фактически
     * "теряет" путь к уже накопленному кэшу тайлов, и снаружи это выглядит как "тайлы
     * никогда не кешируются". Экраны с картой поэтому НЕ должны вызывать .load() сами.
     */
    private fun configureOsmdroid() {
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        val cacheDir = getExternalFilesDir("osmdroid_tiles") ?: cacheDir
        Configuration.getInstance().osmdroidBasePath = cacheDir
        Configuration.getInstance().osmdroidTileCache = cacheDir
        // Явно фиксируем лимиты (по умолчанию в OSMDroid ~600МБ без TTL — тайлы живут,
        // пока не превышен лимит по размеру, а не по времени)
        Configuration.getInstance().tileFileSystemCacheMaxBytes = TILE_CACHE_MAX_BYTES
        Configuration.getInstance().tileFileSystemCacheTrimBytes = TILE_CACHE_TRIM_BYTES

        AppLogger.log("Cache", "Кэш тайлов: ${cacheDir.absolutePath}, лимит ${TILE_CACHE_MAX_BYTES / 1024 / 1024}МБ")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun applySavedTheme() {
        appScope.launch {
            settingsRepository.theme.collectLatest { theme ->
                val mode = when (theme) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }

    /**
     * Ключевая логика п.1.6 ТЗ: сервис геолокации запускается автоматически,
     * как только в базе появляется хотя бы одна активная задача, и
     * останавливается, когда активных задач не остаётся.
     */
    private fun observeActiveTaskCount() {
        appScope.launch {
            var previousCount = -1
            reminderRepository.observeActiveCount().collectLatest { count ->
                if (previousCount <= 0 && count > 0) {
                    startLocationService()
                } else if (previousCount != 0 && count == 0) {
                    stopLocationService()
                }
                previousCount = count
            }
        }
    }

    private fun startLocationService() {
        AppLogger.log("GeoApp", "Активных задач > 0 — запускаю foreground-сервис геолокации")
        val intent = Intent(this, LocationForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopLocationService() {
        AppLogger.log("GeoApp", "Активных задач нет — останавливаю сервис геолокации")
        stopService(Intent(this, LocationForegroundService::class.java))
    }

    companion object {
        private const val TILE_CACHE_MAX_BYTES = 300L * 1024 * 1024 // 300 МБ
        private const val TILE_CACHE_TRIM_BYTES = 250L * 1024 * 1024 // до скольки чистим при превышении лимита

        fun from(context: Context): GeoApp = context.applicationContext as GeoApp
    }
}
