package com.example.cloble.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NominatimGeocoderUnitTest {

    @Test
    fun parseSearchJson_parsesIndonesianLocationsWithShortLabels() {
        val json = """
            [
              {
                "lat": "-7.79558",
                "lon": "110.36949",
                "display_name": "Sleman, Daerah Istimewa Yogyakarta, Jawa, Indonesia",
                "address": {
                  "county": "Sleman",
                  "state": "Daerah Istimewa Yogyakarta"
                }
              },
              {
                "lat": "-6.9175",
                "lon": "107.6191",
                "display_name": "Coblong, Kota Bandung, Jawa Barat, Indonesia",
                "address": {
                  "suburb": "Coblong",
                  "city": "Kota Bandung",
                  "state": "Jawa Barat"
                }
              }
            ]
        """.trimIndent()

        val results = NominatimGeocoder.parseSearchJson(json)
        assertEquals(2, results.size)
        assertEquals("Sleman, Daerah Istimewa Yogyakarta", results[0].shortLabel)
        assertEquals(-7.79558, results[0].latitude, 0.0001)
        assertEquals(110.36949, results[0].longitude, 0.0001)
        assertEquals("Coblong, Kota Bandung", results[1].shortLabel)
    }

    @Test
    fun parseReverseJson_extractsShortLabelAndHandlesErrors() {
        val validJson = """
            {
              "lat": "-6.8722",
              "lon": "107.5425",
              "display_name": "Cimahi Tengah, Kota Cimahi, Jawa Barat, Indonesia",
              "address": {
                "city_district": "Cimahi Tengah",
                "city": "Kota Cimahi",
                "state": "Jawa Barat"
              }
            }
        """.trimIndent()

        val resolved = NominatimGeocoder.parseReverseJson(validJson, -6.87, 107.54)
        assertNotNull(resolved)
        assertEquals("Cimahi Tengah, Kota Cimahi", resolved?.shortLabel)
        assertEquals(-6.8722, resolved?.latitude ?: 0.0, 0.0001)

        val errorJson = """{"error": "Unable to geocode"}"""
        assertNull(NominatimGeocoder.parseReverseJson(errorJson, -6.87, 107.54))
        assertNull(NominatimGeocoder.parseReverseJson("", -6.87, 107.54))
    }
}
