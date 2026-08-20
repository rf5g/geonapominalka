package com.example.geonapominalka.util

import com.google.android.gms.location.DetectedActivity
import kotlin.math.max
import kotlin.math.min

/**
 * Расчёт интервала опроса местоположения по адаптивному алгоритму (см. обсуждение с
 * пользователем — формула, приоритеты правил и защита от деления на ноль зафиксированы
 * до реализации).
 *
 * rawInterval = d / (v * K), затем clamp в [MIN, MAX], затем корректировки:
 *  - IN_VEHICLE побеждает всегда (÷2) — риск проскочить зону важнее экономии батареи;
 *  - иначе если v < 0.5 м/с — ×1.5 (экономия, пользователь почти не двигается);
 *  - прогноз времени до входа в зону (d в формуле — всегда до ЦЕНТРА задачи, как в
 *    базовой формуле, а не до края) — если он меньше уже посчитанного интервала,
 *    интервал сокращается вдвое от прогноза, чтобы не пропустить вход в зону.
 */
object AdaptiveIntervalCalculator {

    /**
     * @param distanceToNearestMeters расстояние до ЦЕНТРА ближайшей активной задачи
     * @param nearestRadiusMeters радиус этой задачи (для прогноза timeToZone)
     * @param velocityMps скорость пользователя, м/с (уже разрешённая — из Activity Recognition
     *        или запасного способа, см. [VelocityResolver])
     * @param activityType текущий тип активности (DetectedActivity.*), если известен
     * @return интервал опроса в секундах
     */
    fun computeIntervalSeconds(
        distanceToNearestMeters: Double,
        nearestRadiusMeters: Int,
        velocityMps: Double,
        activityType: Int?
    ): Int {
        // 1. Защита от деления на ноль — скорость не может быть ниже минимального порога
        val safeVelocity = max(velocityMps, Constants.ADAPTIVE_MIN_VELOCITY_MPS)

        // 2. Базовый интервал по формуле, расстояние — всегда до центра задачи
        val rawInterval = distanceToNearestMeters / (safeVelocity * Constants.ADAPTIVE_K)
        var interval = clamp(rawInterval)

        // 3. Корректировка по активности. IN_VEHICLE побеждает всегда, даже если
        //    формально v < 0.5 (например, стоит в пробке на светофоре с работающим двигателем) —
        //    машина может тронуться в любой момент, риск пропустить зону важнее экономии.
        interval = when {
            activityType == DetectedActivity.IN_VEHICLE ->
                max(interval / Constants.ADAPTIVE_VEHICLE_DIVISOR, Constants.ADAPTIVE_MIN_INTERVAL_SEC.toDouble())
            velocityMps < Constants.ADAPTIVE_SLOW_SPEED_THRESHOLD_MPS ->
                interval * Constants.ADAPTIVE_SLOW_SPEED_MULTIPLIER
            else -> interval
        }
        interval = clamp(interval)

        // 4. Прогноз входа в зону: если скорость заметна и мы приближаемся к зоне быстрее,
        //    чем сработает уже посчитанный интервал — сокращаем интервал вдвое от прогноза.
        if (velocityMps > Constants.ADAPTIVE_SLOW_SPEED_THRESHOLD_MPS && distanceToNearestMeters > nearestRadiusMeters) {
            val timeToZone = (distanceToNearestMeters - nearestRadiusMeters) / safeVelocity
            if (timeToZone < interval) {
                interval = max(timeToZone / 2.0, Constants.ADAPTIVE_MIN_INTERVAL_SEC.toDouble())
            }
        }

        return clamp(interval).toInt()
    }

    private fun clamp(value: Double): Double =
        min(max(value, Constants.ADAPTIVE_MIN_INTERVAL_SEC.toDouble()), Constants.ADAPTIVE_MAX_INTERVAL_SEC.toDouble())

    /** true, если разница между новым и текущим интервалом больше порога — стоит пересоздать LocationRequest. */
    fun shouldUpdate(currentIntervalSeconds: Int, newIntervalSeconds: Int): Boolean {
        if (currentIntervalSeconds <= 0) return true
        val diffRatio = kotlin.math.abs(newIntervalSeconds - currentIntervalSeconds).toDouble() / currentIntervalSeconds
        return diffRatio > Constants.ADAPTIVE_INTERVAL_CHANGE_THRESHOLD
    }

    /** Типовая скорость по распознанной активности — "предпочтительный способ" оценки v по ТЗ. */
    fun speedForActivity(activityType: Int): Double = when (activityType) {
        DetectedActivity.STILL -> Constants.SPEED_STILL_MPS
        DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> Constants.SPEED_WALKING_MPS
        DetectedActivity.RUNNING -> Constants.SPEED_RUNNING_MPS
        DetectedActivity.ON_BICYCLE -> Constants.SPEED_ON_BICYCLE_MPS
        DetectedActivity.IN_VEHICLE -> Constants.SPEED_IN_VEHICLE_MPS
        else -> Constants.SPEED_DEFAULT_MPS
    }

    /** true для "движущихся" типов активности — нужно для триггера STILL -> движение. */
    fun isMoving(activityType: Int): Boolean = activityType in setOf(
        DetectedActivity.WALKING, DetectedActivity.ON_FOOT, DetectedActivity.RUNNING,
        DetectedActivity.ON_BICYCLE, DetectedActivity.IN_VEHICLE
    )
}
