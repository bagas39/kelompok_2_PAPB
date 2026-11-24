package com.example.cloble.domain

import com.example.cloble.domain.util.DistanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUtilUnitTest {

    @Test
    fun distanceCalculator_computesAccurateDistances() {

        val distBandungCimahi = DistanceCalculator.calculateKm(-6.9175, 107.6191, -6.8722, 107.5425)
        assertEquals(10, distBandungCimahi)

        val distBandungDago = DistanceCalculator.calculateKm(-6.9175, 107.6191, -6.8915, 107.6107)
        assertEquals(3, distBandungDago)

        val distBandungJakarta = DistanceCalculator.calculateKm(-6.9175, 107.6191, -6.2088, 106.8456)
        assertTrue(distBandungJakarta in 115..125)

        assertEquals(0, DistanceCalculator.calculateKm(-6.9175, 107.6191, -6.9175, 107.6191))
    }

    @Test
    fun distanceCalculator_resolvesFallbackCoordinatesAndFormatsLabels() {
        val (malangLat, malangLon) = DistanceCalculator.resolveEffectiveCoordinates(
            city = "Arjosari, Kota Malang",
            latitude = -6.9175,
            longitude = 107.6191
        )
        assertTrue(malangLat < -7.5 && malangLon > 112.0)

        val (bandungLat, bandungLon) = DistanceCalculator.resolveEffectiveCoordinates(
            city = "Dago, Bandung",
            latitude = -6.9175,
            longitude = 107.6191
        )
        assertEquals(-6.9175, bandungLat, 0.0001)
        assertEquals(107.6191, bandungLon, 0.0001)

        val (exactLat, exactLon) = DistanceCalculator.resolveEffectiveCoordinates(
            city = "Arjosari, Kota Malang",
            latitude = -7.9318,
            longitude = 112.6560
        )
        assertEquals(-7.9318, exactLat, 0.0001)
        assertEquals(112.6560, exactLon, 0.0001)

        assertEquals("< 1 km", DistanceCalculator.formatDistanceLabel(0))
        assertEquals("1 km", DistanceCalculator.formatDistanceLabel(1))
        assertEquals("12 km", DistanceCalculator.formatDistanceLabel(12))
    }
}
