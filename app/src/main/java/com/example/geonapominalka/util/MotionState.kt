package com.example.geonapominalka.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Текущий тип активности (DetectedActivity.*) от Activity Recognition — общее состояние
 * между ActivityTransitionReceiver (получает системные broadcast) и LocationForegroundService
 * (использует его для адаптивного интервала). In-memory синглтон в рамках процесса — этого
 * достаточно, сервис и ресивер работают в одном процессе.
 */
object MotionState {
    private val _currentActivityType = MutableStateFlow<Int?>(null)
    val currentActivityType: StateFlow<Int?> = _currentActivityType.asStateFlow()

    fun update(activityType: Int) {
        _currentActivityType.value = activityType
    }
}
