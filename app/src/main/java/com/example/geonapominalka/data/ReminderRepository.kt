package com.example.geonapominalka.data

import kotlinx.coroutines.flow.Flow

class ReminderRepository(private val dao: ReminderDao) {

    fun observeActive(): Flow<List<Reminder>> = dao.observeByStatus(ReminderStatus.ACTIVE)
    fun observeDone(): Flow<List<Reminder>> = dao.observeByStatus(ReminderStatus.DONE)
    fun observeAll(): Flow<List<Reminder>> = dao.observeAll()

    // Наблюдается в GeoApp — по этому счётчику принимается решение
    // запускать или останавливать LocationForegroundService.
    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    suspend fun getById(id: Long): Reminder? = dao.getById(id)

    suspend fun add(reminder: Reminder): Long = dao.insert(reminder)

    suspend fun update(reminder: Reminder) = dao.update(reminder)

    suspend fun delete(reminder: Reminder) = dao.delete(reminder)

    suspend fun resetAll() = dao.deleteAll()

    suspend fun markDone(id: Long) = dao.setStatus(id, ReminderStatus.DONE)

    suspend fun getActiveOnce(): List<Reminder> = dao.getActiveOnce()

    suspend fun recordNotification(id: Long, time: Long, inside: Boolean) =
        dao.updateNotificationState(id, time, inside)

    suspend fun setZoneState(id: Long, inside: Boolean) = dao.updateZoneState(id, inside)
}
