package com.example.geonapominalka.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.geonapominalka.GeoApp
import com.example.geonapominalka.R
import com.example.geonapominalka.data.Reminder
import com.example.geonapominalka.receiver.NotificationActionReceiver
import com.example.geonapominalka.ui.MainActivity
import com.example.geonapominalka.util.Constants
import com.example.geonapominalka.util.LocationUtils
import com.example.geonapominalka.util.AppLogger
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Foreground-сервис, отслеживающий текущее местоположение и сверяющий его
 * со всеми активными задачами из БД. При входе в радиус — уведомление
 * (см. п.1.5, 1.6, 3 ТЗ). Сервис запускается/останавливается из GeoApp
 * в зависимости от количества активных задач, поэтому сам он не принимает
 * решения "нужен ли я" — только выполняет свою работу, пока жив.
 */
class LocationForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private val app: GeoApp by lazy { GeoApp.from(this) }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        serviceScope.launch {
            val intervalSeconds = app.settingsRepository.currentIntervalSeconds()
            AppLogger.log("Location", "Сервис запущен, интервал опроса: $intervalSeconds сек")
            startLocationUpdates(intervalSeconds)
        }
        // START_STICKY: система пересоздаст сервис, если он был убит,
        // пока есть активные задачи (GeoApp снова его запустит при необходимости).
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(Constants.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationUpdates(intervalSeconds: Int) {
        // Для длинных интервалов используем режим экономии батареи (п.1.6 ТЗ)
        val priority = if (intervalSeconds >= 60) {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        } else {
            Priority.PRIORITY_HIGH_ACCURACY
        }

        val request = LocationRequest.Builder(intervalSeconds * 1000L)
            .setPriority(priority)
            .setMinUpdateIntervalMillis(intervalSeconds * 1000L / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                AppLogger.log(
                    "Location",
                    "Получены координаты: %.5f, %.5f (точность %.0fм)".format(
                        location.latitude, location.longitude, location.accuracy
                    )
                )
                serviceScope.launch { checkGeofences(location.latitude, location.longitude) }
            }
        }
        locationCallback = callback

        try {
            fusedClient.requestLocationUpdates(request, callback, mainLooper)
        } catch (e: SecurityException) {
            // Разрешение на геолокацию отсутствует — сервис не может работать.
            stopSelf()
        }
    }

    /** Основная логика геозон: перебираем активные задачи, сравниваем расстояние с радиусом. */
    private suspend fun checkGeofences(userLat: Double, userLng: Double) {
        val activeReminders = app.reminderRepository.getActiveOnce()
        for (reminder in activeReminders) {
            val distance = LocationUtils.distanceMeters(userLat, userLng, reminder.latitude, reminder.longitude)
            val inRadius = distance <= reminder.radius

            if (inRadius && !reminder.isInsideZone) {
                // Только что вошли в зону
                AppLogger.log(
                    "Geofence",
                    "Вход в зону «${reminder.name}»: расстояние ${distance.toInt()}м, радиус ${reminder.radius}м"
                )
                maybeNotify(reminder)
            } else if (!inRadius && reminder.isInsideZone) {
                // Вышли из зоны (с гистерезисом) — сбрасываем флаг, чтобы при повторном
                // входе уведомление сработало снова.
                if (distance > reminder.radius + Constants.EXIT_HYSTERESIS_METERS) {
                    AppLogger.log(
                        "Geofence",
                        "Выход из зоны «${reminder.name}»: расстояние ${distance.toInt()}м"
                    )
                    app.reminderRepository.setZoneState(reminder.id, false)
                }
            } else if (inRadius && reminder.isInsideZone) {
                // Остаёмся внутри — проверяем cooldown на случай, если флаг не сбрасывался
                maybeNotify(reminder)
            }
        }
    }

    private suspend fun maybeNotify(reminder: Reminder) {
        val now = System.currentTimeMillis()
        val cooldownPassed = now - reminder.lastNotificationTime >= Constants.NOTIFICATION_COOLDOWN_MS
        if (!reminder.isInsideZone || cooldownPassed) {
            if (cooldownPassed) {
                AppLogger.log("Geofence", "Уведомление отправлено для «${reminder.name}»")
                showReminderNotification(reminder)
                app.reminderRepository.recordNotification(reminder.id, now, true)
            } else {
                app.reminderRepository.setZoneState(reminder.id, true)
            }
        }
    }

    private suspend fun showReminderNotification(reminder: Reminder) {
        val notificationId = Constants.REMINDER_NOTIFICATION_ID_BASE + reminder.id.toInt()

        val contentIntent = PendingIntent.getActivity(
            this, notificationId,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = PendingIntent.getBroadcast(
            this, notificationId * 10 + 1,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = Constants.ACTION_DONE
                putExtra(Constants.EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            this, notificationId * 10 + 2,
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = Constants.ACTION_SNOOZE
                putExtra(Constants.EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Настройки звука/вибрации берём из SettingsRepository (п.1.7 ТЗ)
        val soundUriString = app.settingsRepository.soundUri.first()
        val soundUri = soundUriString?.let { android.net.Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val vibrationEnabled = app.settingsRepository.vibration.first()

        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.name)
            .setContentText(reminder.description.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.description.orEmpty()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.action_done), doneIntent)
            .addAction(0, getString(R.string.action_snooze), snoozeIntent)
            .setSound(soundUri)

        if (vibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 300, 200, 300))
        }

        NotificationManagerCompat.from(this).notify(notificationId, builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.log("Location", "Сервис остановлен, опрос геолокации прекращён")
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
