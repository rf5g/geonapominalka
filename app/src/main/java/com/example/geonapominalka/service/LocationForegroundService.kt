package com.example.geonapominalka.service

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.geonapominalka.GeoApp
import com.example.geonapominalka.R
import com.example.geonapominalka.data.Reminder
import com.example.geonapominalka.receiver.NotificationActionReceiver
import com.example.geonapominalka.ui.MainActivity
import com.example.geonapominalka.util.AdaptiveIntervalCalculator
import com.example.geonapominalka.util.AppLogger
import com.example.geonapominalka.util.Constants
import com.example.geonapominalka.util.LocationUtils
import com.example.geonapominalka.util.MotionState
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Foreground-сервис, отслеживающий текущее местоположение и сверяющий его
 * со всеми активными задачами из БД. При входе в радиус — уведомление
 * (см. п.1.5, 1.6, 3 ТЗ). Сервис запускается/останавливается из GeoApp
 * в зависимости от количества активных задач, поэтому сам он не принимает
 * решения "нужен ли я" — только выполняет свою работу, пока жив.
 *
 * Интервал опроса — либо ручной (из настроек, применяется "живьём" при изменении
 * пользователем без перезапуска сервиса), либо адаптивный (формула
 * d/(v*K) с поправками, см. AdaptiveIntervalCalculator) — выбор между ними тоже
 * читается из настроек в реальном времени.
 */
class LocationForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var locationCallback: LocationCallback? = null

    private var currentAppliedIntervalSeconds: Int = 60
    private var adaptiveModeEnabled = false
    private var activityTransitionsRegistered = false
    private var settingsObserverStarted = false

    private val app: GeoApp by lazy { GeoApp.from(this) }

    private val activityTransitionPendingIntent: PendingIntent by lazy {
        val intent = Intent(this, ActivityTransitionReceiver::class.java)
        PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        activityRecognitionClient = ActivityRecognition.getClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()

        if (!settingsObserverStarted) {
            settingsObserverStarted = true
            observeSettings()
            observeMotionForInstantTrigger()
        }

        if (locationCallback == null) {
            serviceScope.launch {
                val manual = app.settingsRepository.currentIntervalSeconds()
                val adaptive = app.settingsRepository.currentAdaptiveMode()
                adaptiveModeEnabled = adaptive
                AppLogger.log(
                    "Location",
                    "Сервис запущен. Режим: " + if (adaptive) "адаптивный" else "ручной, $manual сек"
                )
                // Стартуем с ручного значения как с "бутстрапа" — если включён адаптивный режим,
                // интервал пересчитается сразу после первого полученного местоположения.
                startLocationUpdates(manual)
                if (adaptive) registerActivityTransitionsIfPermitted()
            }
        }

        // START_STICKY: система пересоздаст сервис, если он был убит,
        // пока есть активные задачи (GeoApp снова его запустит при необходимости).
        return START_STICKY
    }

    /** Следим за настройками "живьём": ручной интервал и переключатель адаптивного режима. */
    private fun observeSettings() {
        serviceScope.launch {
            app.settingsRepository.adaptiveMode
                .combine(app.settingsRepository.intervalSeconds) { adaptive, manual -> adaptive to manual }
                .collectLatest { (adaptive, manual) ->
                    val modeChanged = adaptive != adaptiveModeEnabled
                    adaptiveModeEnabled = adaptive

                    if (!adaptive) {
                        if (modeChanged) {
                            AppLogger.log("Location", "Адаптивный режим выключен, интервал: $manual сек")
                            unregisterActivityTransitions()
                            restartLocationUpdates(manual)
                        } else if (manual != currentAppliedIntervalSeconds) {
                            // Правка из п.6 обсуждения: интервал, изменённый пользователем вручную,
                            // применяется сразу ко всем активным задачам без перезапуска сервиса.
                            AppLogger.log("Location", "Интервал изменён пользователем: $currentAppliedIntervalSeconds сек → $manual сек")
                            restartLocationUpdates(manual)
                        }
                    } else if (modeChanged) {
                        AppLogger.log("Location", "Адаптивный режим включён")
                        registerActivityTransitionsIfPermitted()
                    }
                }
        }
    }

    /** Мгновенный триггер: переход из состояния "стоит" в любое движение -> внеочередной опрос местоположения. */
    private fun observeMotionForInstantTrigger() {
        serviceScope.launch {
            var previousMoving: Boolean? = null
            MotionState.currentActivityType.collectLatest { type ->
                if (type == null) return@collectLatest
                val moving = AdaptiveIntervalCalculator.isMoving(type)
                if (adaptiveModeEnabled && previousMoving == false && moving) {
                    AppLogger.log("Motion", "Начало движения — внеочередной запрос местоположения")
                    requestSingleLocationUpdate()
                }
                previousMoving = moving
            }
        }
    }

    private fun requestSingleLocationUpdate() {
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { location -> if (location != null) handleNewLocation(location) }
        } catch (e: SecurityException) {
            // Разрешение отсутствует — тихо игнорируем, штатный опрос всё равно продолжит работать (или нет — см. startLocationUpdates)
        }
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
        currentAppliedIntervalSeconds = intervalSeconds

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
                handleNewLocation(location)
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

    /** Пересоздаёт LocationRequest с новым интервалом (ручное изменение или пересчёт адаптивного). */
    private fun restartLocationUpdates(newIntervalSeconds: Int) {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        startLocationUpdates(newIntervalSeconds)
    }

    private fun handleNewLocation(location: Location) {
        AppLogger.log(
            "Location",
            "Получены координаты: %.5f, %.5f (точность %.0fм)".format(
                location.latitude, location.longitude, location.accuracy
            )
        )
        serviceScope.launch {
            if (adaptiveModeEnabled) recomputeAdaptiveInterval(location)
            checkGeofences(location.latitude, location.longitude)
        }
    }

    /** Пересчёт адаптивного интервала по формуле d/(v*K) с поправками — см. AdaptiveIntervalCalculator. */
    private suspend fun recomputeAdaptiveInterval(location: Location) {
        val activeReminders = app.reminderRepository.getActiveOnce()
        if (activeReminders.isEmpty()) return

        var nearestDistance = Double.MAX_VALUE
        var nearestRadius = 200
        for (reminder in activeReminders) {
            val distance = LocationUtils.distanceMeters(
                location.latitude, location.longitude, reminder.latitude, reminder.longitude
            ).toDouble()
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestRadius = reminder.radius
            }
        }

        val activityType = MotionState.currentActivityType.value
        val velocity = resolveVelocity(location, activityType)
        val newInterval = AdaptiveIntervalCalculator.computeIntervalSeconds(
            nearestDistance, nearestRadius, velocity, activityType
        )

        if (AdaptiveIntervalCalculator.shouldUpdate(currentAppliedIntervalSeconds, newInterval)) {
            AppLogger.log(
                "Adaptive",
                "d=${nearestDistance.toInt()}м, v=%.1f м/с, активность=%s → интервал %dс (было %dс)".format(
                    velocity, activityName(activityType), newInterval, currentAppliedIntervalSeconds
                )
            )
            restartLocationUpdates(newInterval)
        }
    }

    /**
     * Оценка скорости: предпочтительно по типу активности (типовая скорость для режима —
     * см. Constants.SPEED_*), запасной способ — скорость из самого GPS-фикса (location.speed),
     * если она есть; иначе средняя скорость пешехода.
     */
    private fun resolveVelocity(location: Location, activityType: Int?): Double = when {
        activityType != null -> AdaptiveIntervalCalculator.speedForActivity(activityType)
        location.hasSpeed() && location.speed > 0f -> location.speed.toDouble()
        else -> Constants.SPEED_DEFAULT_MPS
    }

    private fun activityName(type: Int?): String = when (type) {
        DetectedActivity.STILL -> "стоит"
        DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> "пешком"
        DetectedActivity.RUNNING -> "бег"
        DetectedActivity.ON_BICYCLE -> "велосипед"
        DetectedActivity.IN_VEHICLE -> "транспорт"
        else -> "неизв."
    }

    private fun registerActivityTransitionsIfPermitted() {
        if (activityTransitionsRegistered) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            AppLogger.log("Motion", "Нет разрешения на распознавание активности — скорость оценивается по GPS")
            return
        }
        val transitions = listOf(
            DetectedActivity.STILL, DetectedActivity.WALKING, DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE, DetectedActivity.IN_VEHICLE
        ).map {
            ActivityTransition.Builder()
                .setActivityType(it)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        }
        val request = ActivityTransitionRequest(transitions)
        try {
            activityRecognitionClient.requestActivityTransitionUpdates(request, activityTransitionPendingIntent)
                .addOnSuccessListener {
                    activityTransitionsRegistered = true
                    AppLogger.log("Motion", "Распознавание активности подключено")
                }
                .addOnFailureListener { e ->
                    AppLogger.log("Motion", "Не удалось подключить распознавание активности: ${e.message}")
                }
        } catch (e: SecurityException) {
            AppLogger.log("Motion", "Нет разрешения на распознавание активности — скорость оценивается по GPS")
        }
    }

    private fun unregisterActivityTransitions() {
        if (!activityTransitionsRegistered) return
        activityRecognitionClient.removeActivityTransitionUpdates(activityTransitionPendingIntent)
        activityTransitionsRegistered = false
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
        unregisterActivityTransitions()
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
