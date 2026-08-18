package com.example.geonapominalka.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: String): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE status = 'active'")
    suspend fun getActiveOnce(): List<Reminder>

    // Используется для решения о запуске/остановке foreground-сервиса:
    // Flow<Int> количества активных задач, на который подписывается GeoApp.
    @Query("SELECT COUNT(*) FROM reminders WHERE status = 'active'")
    fun observeActiveCount(): Flow<Int>

    @Query("UPDATE reminders SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("UPDATE reminders SET lastNotificationTime = :time, isInsideZone = :inside WHERE id = :id")
    suspend fun updateNotificationState(id: Long, time: Long, inside: Boolean)

    @Query("UPDATE reminders SET isInsideZone = :inside WHERE id = :id")
    suspend fun updateZoneState(id: Long, inside: Boolean)
}
