package com.example.geonapominalka.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Статус напоминания.
 * ACTIVE  — задача активна и отслеживается сервисом.
 * DONE    — задача выполнена, больше не отслеживается (маркер удалён с карты).
 *
 * "Отложить" не меняет статус — задача остаётся ACTIVE, но обновляется lastNotificationTime,
 * чтобы не спамить повторными уведомлениями (см. п.1.5 ТЗ).
 */
object ReminderStatus {
    const val ACTIVE = "active"
    const val DONE = "done"
}

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val latitude: Double,
    val longitude: Double,
    val radius: Int = 200,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = ReminderStatus.ACTIVE,
    // Время последнего показанного уведомления (для анти-спама, гистерезис 10 минут)
    val lastNotificationTime: Long = 0L,
    // Флаг "пользователь сейчас внутри зоны" — нужен, чтобы повторно уведомлять
    // только после выхода и повторного входа в радиус.
    val isInsideZone: Boolean = false
)
