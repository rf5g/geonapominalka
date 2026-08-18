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

    // Анти-спам: не повторять уведомление чаще, чем раз в 10 минут для одной задачи
    const val NOTIFICATION_COOLDOWN_MS = 10 * 60 * 1000L

    // Гистерезис при выходе из зоны, чтобы не дёргалось на границе радиуса
    const val EXIT_HYSTERESIS_METERS = 15
}
