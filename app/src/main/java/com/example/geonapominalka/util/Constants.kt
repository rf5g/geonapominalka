package com.example.geonapominalka.util

object Constants {
    const val NOTIFICATION_CHANNEL_ID = "geo_reminders_channel"
    const val FOREGROUND_NOTIFICATION_ID = 1000
    // ID уведомлений о срабатывании геозоны = 2000 + reminder.id, чтобы не пересекались
    const val REMINDER_NOTIFICATION_ID_BASE = 2000

    const val ACTION_DONE = "com.example.geonapominalka.action.DONE"
    const val ACTION_SNOOZE = "com.example.geonapominalka.action.SNOOZE"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"

    const val EXTRA_PICKED_LAT = "extra_picked_lat"
    const val EXTRA_PICKED_LNG = "extra_picked_lng"
    const val EXTRA_EDIT_REMINDER_ID = "extra_edit_reminder_id"
    const val EXTRA_INITIAL_LAT = "extra_initial_lat"
    const val EXTRA_INITIAL_LNG = "extra_initial_lng"
    const val EXTRA_INITIAL_ADDRESS = "extra_initial_address"

    // Анти-спам: не повторять уведомление чаще, чем раз в 10 минут для одной задачи
    const val NOTIFICATION_COOLDOWN_MS = 10 * 60 * 1000L

    // Гистерезис при выходе из зоны, чтобы не дёргалось на границе радиуса
    const val EXIT_HYSTERESIS_METERS = 15

    // --- Адаптивный алгоритм опроса местоположения ---
    const val ADAPTIVE_MIN_INTERVAL_SEC = 15
    const val ADAPTIVE_MAX_INTERVAL_SEC = 900 // 15 минут
    const val ADAPTIVE_K = 6.0
    // Защита от деления на ноль в rawInterval = d / (v * K): скорость никогда не считаем ниже этого порога
    const val ADAPTIVE_MIN_VELOCITY_MPS = 0.1
    // Порог "стоит/идёт очень медленно" — увеличиваем интервал в 1.5 раза
    const val ADAPTIVE_SLOW_SPEED_THRESHOLD_MPS = 0.5
    const val ADAPTIVE_SLOW_SPEED_MULTIPLIER = 1.5
    const val ADAPTIVE_VEHICLE_DIVISOR = 2.0
    // Пересоздаём LocationRequest только если новый интервал отличается от текущего более чем на столько
    const val ADAPTIVE_INTERVAL_CHANGE_THRESHOLD = 0.2

    // Типовые скорости (м/с) для режимов Activity Recognition — используются как оценка v,
    // когда есть распознанная активность (это "предпочтительный способ" по ТЗ)
    const val SPEED_STILL_MPS = 0.0
    const val SPEED_WALKING_MPS = 1.4
    const val SPEED_RUNNING_MPS = 3.0
    const val SPEED_ON_BICYCLE_MPS = 5.0
    const val SPEED_IN_VEHICLE_MPS = 15.0
    // Если скорость определить невозможно вообще никак — средняя скорость пешехода
    const val SPEED_DEFAULT_MPS = 1.4
}
