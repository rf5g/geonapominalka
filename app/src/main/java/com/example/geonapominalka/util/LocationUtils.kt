package com.example.geonapominalka.util

import android.location.Location

object LocationUtils {
    /** Расстояние в метрах между двумя точками (формула через Android Location API). */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }
}
