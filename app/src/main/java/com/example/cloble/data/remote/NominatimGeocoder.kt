package com.example.cloble.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ResolvedLocation(
    val displayName: String,
    val shortLabel: String,
    val latitude: Double,
    val longitude: Double
)

object NominatimGeocoder {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun searchPlaces(query: String, limit: Int = 6): List<ResolvedLocation> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val primaryUrl = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&accept-language=id&countrycodes=id&limit=$limit&q=$encoded"
        val primaryBody = fetchBody(primaryUrl)
        val primaryResults = parseSearchJson(primaryBody)
        if (primaryResults.isNotEmpty()) {
            return@withContext primaryResults
        }
        val fallbackUrl = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&accept-language=id&limit=$limit&q=$encoded"
        val fallbackBody = fetchBody(fallbackUrl)
        parseSearchJson(fallbackBody)
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): ResolvedLocation? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&accept-language=id&lat=$latitude&lon=$longitude"
        val body = fetchBody(url) ?: return@withContext null
        parseReverseJson(body, latitude, longitude)
    }

    private fun fetchBody(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CloBleAndroid/1.0 (contact@cloble.app)")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseSearchJson(jsonString: String?): List<ResolvedLocation> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<ResolvedLocation>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                parseLocationObject(obj, null, null)?.let { list.add(it) }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseReverseJson(
        jsonString: String?,
        fallbackLat: Double,
        fallbackLon: Double
    ): ResolvedLocation? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(jsonString)
            if (obj.has("error")) return null
            parseLocationObject(obj, fallbackLat, fallbackLon)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLocationObject(
        obj: JSONObject,
        fallbackLat: Double?,
        fallbackLon: Double?
    ): ResolvedLocation? {
        val lat = obj.optString("lat").toDoubleOrNull()
            ?: if (obj.has("lat") && !obj.isNull("lat")) obj.optDouble("lat", Double.NaN) else Double.NaN
        val lon = obj.optString("lon").toDoubleOrNull()
            ?: if (obj.has("lon") && !obj.isNull("lon")) obj.optDouble("lon", Double.NaN) else Double.NaN
        val resolvedLat = if (!lat.isNaN()) lat else fallbackLat ?: return null
        val resolvedLon = if (!lon.isNaN()) lon else fallbackLon ?: return null

        val displayName = obj.optString("display_name").trim()
        val addressObj = obj.optJSONObject("address")
        val shortLabel = buildShortLabel(addressObj, displayName)
        if (shortLabel.isBlank() && displayName.isBlank()) return null

        return ResolvedLocation(
            displayName = displayName.ifBlank { shortLabel },
            shortLabel = shortLabel.ifBlank { displayName },
            latitude = resolvedLat,
            longitude = resolvedLon
        )
    }

    internal fun buildShortLabel(addressObj: JSONObject?, displayName: String): String {
        if (addressObj != null) {
            val locality = listOf(
                "suburb",
                "city_district",
                "village",
                "town",
                "municipality",
                "county"
            ).firstNotNullOfOrNull { key ->
                addressObj.optString(key).trim().takeIf { it.isNotEmpty() }
            }
            val city = listOf(
                "city",
                "town",
                "county",
                "state_district",
                "state"
            ).firstNotNullOfOrNull { key ->
                addressObj.optString(key).trim().takeIf { it.isNotEmpty() }
            }
            val province = addressObj.optString("state").trim().takeIf { it.isNotEmpty() }

            val parts = linkedSetOf<String>()
            if (locality != null) parts.add(locality)
            if (city != null && !city.equals(locality, ignoreCase = true)) {
                parts.add(city)
            } else if (province != null && !province.equals(locality, ignoreCase = true)) {
                parts.add(province)
            }
            if (parts.isNotEmpty()) {
                return parts.take(2).joinToString(", ")
            }
        }

        val fallbackParts = displayName
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return fallbackParts.take(2).joinToString(", ")
    }
}
