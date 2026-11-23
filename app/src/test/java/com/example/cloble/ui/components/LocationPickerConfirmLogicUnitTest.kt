package com.example.cloble.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPickerConfirmLogicUnitTest {

    @Test
    fun buildLocationConfirmPlan_ignoresMidAnimationMapCenterDuringProgrammaticMove() {
        val plan = buildLocationConfirmPlan(
            cleanCity = "Medan, Sumatera Utara",
            cleanSearchQuery = "",
            selectedLat = 3.5952,
            selectedLon = 98.6722,
            mapCenterLat = -1.5,
            mapCenterLon = 103.0,
            normalizedInitLat = -6.9175,
            normalizedInitLon = 107.6191,
            hasUserEditedCity = false,
            hasUserMovedPin = true,
            withinProgrammaticMoveWindow = true
        )

        assertFalse(plan.needsGeocode)
        assertEquals(3.5952, plan.baseLat, 1e-6)
        assertEquals(98.6722, plan.baseLon, 1e-6)
    }

    @Test
    fun buildLocationConfirmPlan_usesSearchQueryWhenPinUnmovedAndCityUnedited() {
        val plan = buildLocationConfirmPlan(
            cleanCity = "Bunulrejo, Kota Malang",
            cleanSearchQuery = "Yogyakarta",
            selectedLat = -7.9688,
            selectedLon = 112.6487,
            mapCenterLat = -7.9688,
            mapCenterLon = 112.6487,
            normalizedInitLat = -7.9688,
            normalizedInitLon = 112.6487,
            hasUserEditedCity = false,
            hasUserMovedPin = false,
            withinProgrammaticMoveWindow = false
        )

        assertTrue(plan.needsGeocode)
        assertTrue(plan.useSearchQuery)
        assertEquals("Yogyakarta", plan.queryText)
    }

    @Test
    fun buildLocationConfirmPlan_usesEditedCityLabelWhenUserTypedCityWithoutMovingPin() {
        val plan = buildLocationConfirmPlan(
            cleanCity = "Sleman, DI Yogyakarta",
            cleanSearchQuery = "Yogyakarta",
            selectedLat = -7.9688,
            selectedLon = 112.6487,
            mapCenterLat = -7.9688,
            mapCenterLon = 112.6487,
            normalizedInitLat = -7.9688,
            normalizedInitLon = 112.6487,
            hasUserEditedCity = true,
            hasUserMovedPin = false,
            withinProgrammaticMoveWindow = false
        )

        assertTrue(plan.needsGeocode)
        assertFalse(plan.useSearchQuery)
        assertEquals("Sleman, DI Yogyakarta", plan.queryText)
    }

    @Test
    fun buildLocationConfirmPlan_usesMapCenterAfterManualDragOutsideProgrammaticWindow() {
        val plan = buildLocationConfirmPlan(
            cleanCity = "Semarang, Jawa Tengah",
            cleanSearchQuery = "",
            selectedLat = -6.9175,
            selectedLon = 107.6191,
            mapCenterLat = -6.9667,
            mapCenterLon = 110.4167,
            normalizedInitLat = -6.9175,
            normalizedInitLon = 107.6191,
            hasUserEditedCity = false,
            hasUserMovedPin = true,
            withinProgrammaticMoveWindow = false
        )

        assertFalse(plan.needsGeocode)
        assertEquals(-6.9667, plan.baseLat, 1e-6)
        assertEquals(110.4167, plan.baseLon, 1e-6)
    }
}
