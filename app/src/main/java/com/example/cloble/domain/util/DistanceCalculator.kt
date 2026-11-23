package com.example.cloble.domain.util

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object DistanceCalculator {
    private const val EARTH_RADIUS_KM = 6371.0
    private const val DEFAULT_BANDUNG_LAT = -6.9175
    private const val DEFAULT_BANDUNG_LON = 107.6191

    private val indonesianCityCenters: List<Pair<List<String>, Pair<Double, Double>>> = listOf(
        listOf("bandung") to (-6.9175 to 107.6191),
        listOf("cimahi") to (-6.8722 to 107.5425),
        listOf("jatinangor", "sumedang") to (-6.9320 to 107.7725),
        listOf("jakarta", "dki jakarta", "kebayoran", "menteng", "senayan", "kemang", "grogol", "kelapa gading") to (-6.2088 to 106.8456),
        listOf("bogor") to (-6.5971 to 106.8060),
        listOf("depok") to (-6.4025 to 106.7942),
        listOf("tangerang", "bsd", "bintaro", "serpong") to (-6.1783 to 106.6319),
        listOf("bekasi") to (-6.2383 to 106.9756),
        listOf("karawang") to (-6.3227 to 107.3376),
        listOf("cirebon") to (-6.7320 to 108.5523),
        listOf("tasikmalaya") to (-7.3506 to 108.2172),
        listOf("garut") to (-7.2279 to 107.9087),
        listOf("sukabumi") to (-6.9277 to 106.9300),
        listOf("semarang") to (-6.9667 to 110.4167),
        listOf("solo", "surakarta") to (-7.5755 to 110.8243),
        listOf("yogyakarta", "jogja", "sleman", "bantul", "kulon progo", "gunung kidul") to (-7.7956 to 110.3695),
        listOf("magelang") to (-7.4797 to 110.2177),
        listOf("purwokerto", "banyumas") to (-7.4243 to 109.2302),
        listOf("tegal") to (-6.8694 to 109.1402),
        listOf("pekalongan") to (-6.8898 to 109.6746),
        listOf("surabaya") to (-7.2575 to 112.7521),
        listOf("sidoarjo") to (-7.4478 to 112.7183),
        listOf("malang", "arjosari", "lowokwaru", "blimbing", "klojen") to (-7.9666 to 112.6326),
        listOf("batu") to (-7.8671 to 112.5239),
        listOf("kediri") to (-7.8480 to 112.0178),
        listOf("jember") to (-8.1845 to 113.6681),
        listOf("banyuwangi") to (-8.2192 to 114.3691),
        listOf("madiun") to (-7.6298 to 111.5239),
        listOf("gresik") to (-7.1567 to 112.6555),
        listOf("denpasar", "bali", "badung", "ubud", "kuta", "gianyar") to (-8.6705 to 115.2126),
        listOf("mataram", "lombok") to (-8.5833 to 116.1167),
        listOf("kupang") to (-10.1772 to 123.6070),
        listOf("medan") to (3.5952 to 98.6722),
        listOf("padang") to (-0.9471 to 100.4172),
        listOf("pekanbaru") to (0.5071 to 101.4478),
        listOf("batam") to (1.0456 to 104.0305),
        listOf("jambi") to (-1.6101 to 103.6131),
        listOf("palembang") to (-2.9761 to 104.7754),
        listOf("bengkulu") to (-3.7928 to 102.2608),
        listOf("bandar lampung", "lampung") to (-5.4500 to 105.2667),
        listOf("pontianak") to (-0.0263 to 109.3425),
        listOf("banjarmasin") to (-3.3194 to 114.5908),
        listOf("balikpapan") to (-1.2379 to 116.8529),
        listOf("samarinda") to (-0.5022 to 117.1536),
        listOf("makassar") to (-5.1477 to 119.4327),
        listOf("manado") to (1.4748 to 124.8421),
        listOf("palu") to (-0.8917 to 119.8707),
        listOf("kendari") to (-3.9985 to 122.5129),
        listOf("ambon") to (-3.6954 to 128.1814),
        listOf("jayapura") to (-2.5916 to 140.6690)
    )

    fun calculateKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        if (lat1 == lat2 && lon1 == lon2) return 0
        val distance = calculateKmExact(lat1, lon1, lat2, lon2)
        if (distance.isNaN()) return 0
        return distance.roundToInt().coerceAtLeast(0)
    }

    fun calculateKmExact(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == lat2 && lon1 == lon2) return 0.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val originLat = Math.toRadians(lat1)
        val destinationLat = Math.toRadians(lat2)

        val a = (sin(dLat / 2.0).pow(2.0) +
                sin(dLon / 2.0).pow(2.0) * cos(originLat) * cos(destinationLat))
            .coerceIn(0.0, 1.0)
        val c = 2.0 * asin(sqrt(a))
        return EARTH_RADIUS_KM * c
    }

    fun resolveEffectiveCoordinates(city: String, latitude: Double, longitude: Double): Pair<Double, Double> {
        val normalizedCity = city.trim().lowercase()
        val isUnsetOrInvalid = latitude.isNaN() ||
                longitude.isNaN() ||
                (abs(latitude) < 1e-6 && abs(longitude) < 1e-6)
        val isDefaultBandungCoords = abs(latitude - DEFAULT_BANDUNG_LAT) < 1e-4 &&
                abs(longitude - DEFAULT_BANDUNG_LON) < 1e-4
        val cityMentionsBandung = normalizedCity.contains("bandung")

        if (isUnsetOrInvalid || (isDefaultBandungCoords && normalizedCity.isNotEmpty() && !cityMentionsBandung)) {
            val matched = findCityCenter(normalizedCity)
            if (matched != null) return matched
        }
        return if (isUnsetOrInvalid) {
            DEFAULT_BANDUNG_LAT to DEFAULT_BANDUNG_LON
        } else {
            latitude to longitude
        }
    }

    fun findCityCenter(cityQuery: String): Pair<Double, Double>? {
        val normalized = cityQuery.trim().lowercase()
        if (normalized.isEmpty()) return null
        return indonesianCityCenters.firstOrNull { (keywords, _) ->
            keywords.any { keyword -> normalized.contains(keyword) }
        }?.second
    }

    fun formatDistanceLabel(distanceKm: Int): String {
        return if (distanceKm <= 0) "< 1 km" else "$distanceKm km"
    }
}
