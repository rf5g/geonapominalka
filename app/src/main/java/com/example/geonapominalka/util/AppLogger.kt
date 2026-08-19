package com.example.geonapominalka.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Простой лог в памяти для экрана "Консоль" (визуальный контроль и отладка).
 * Показывает, что реально делает сервис геолокации: старт/остановка, проверка
 * геозон, отправленные уведомления, действия по кнопкам, выданные разрешения —
 * всё то, что иначе видно только в logcat через кабель.
 *
 * Живёт как in-memory синглтон в рамках процесса приложения — этого достаточно,
 * так как foreground-сервис работает в том же процессе, что и UI (отдельный
 * :process в манифесте не объявлен).
 */
object AppLogger {

    data class Entry(val timestamp: Long, val tag: String, val message: String)

    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Synchronized
    fun log(tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), tag, message)
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        Log.d(tag, message)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
