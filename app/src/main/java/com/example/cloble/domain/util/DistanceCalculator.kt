package com.example.cloble.domain.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object DistanceCalculator {
    private const val EARTH_RADIUS_KM = 6371.0

    fun calculateKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        if (lat1 == lat2 && lon1 == lon2) return 0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val originLat = Math.toRadians(lat1)
        val destinationLat = Math.toRadians(lat2)

        val a = sin(dLat / 2.0).pow(2.0) +
                sin(dLon / 2.0).pow(2.0) * cos(originLat) * cos(destinationLat)
        val c = 2.0 * asin(sqrt(a))
        val distance = EARTH_RADIUS_KM * c

        return distance.roundToInt().coerceAtLeast(0)
    }

    fun calculateKmExact(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == lat2 && lon1 == lon2) return 0.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val originLat = Math.toRadians(lat1)
        val destinationLat = Math.toRadians(lat2)

        val a = sin(dLat / 2.0).pow(2.0) +
                sin(dLon / 2.0).pow(2.0) * cos(originLat) * cos(destinationLat)
        val c = 2.0 * asin(sqrt(a))
        return EARTH_RADIUS_KM * c
    }
}
