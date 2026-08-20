package com.example.geonapominalka.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.geonapominalka.util.AppLogger
import com.example.geonapominalka.util.MotionState
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Получает системные события смены активности (Activity Recognition) и обновляет
 * общее состояние MotionState. Регистрируется на ENTER-события каждого из
 * отслеживаемых типов (STILL/WALKING/RUNNING/ON_BICYCLE/IN_VEHICLE) — этого достаточно,
 * чтобы всегда знать актуальный тип по последнему пришедшему событию, без EXIT-событий.
 */
class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            MotionState.update(event.activityType)
            AppLogger.log("Motion", "Активность: ${activityName(event.activityType)}")
        }
    }

    private fun activityName(type: Int): String = when (type) {
        DetectedActivity.STILL -> "стоит на месте"
        DetectedActivity.WALKING -> "идёт пешком"
        DetectedActivity.ON_FOOT -> "пешком"
        DetectedActivity.RUNNING -> "бежит"
        DetectedActivity.ON_BICYCLE -> "едет на велосипеде"
        DetectedActivity.IN_VEHICLE -> "едет на транспорте"
        else -> "неизвестно ($type)"
    }
}
