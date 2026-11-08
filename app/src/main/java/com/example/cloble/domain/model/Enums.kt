package com.example.cloble.domain.model

enum class ItemStatus {
    AVAILABLE,
    EXCHANGED,
    HIDDEN_DELETED
}

enum class InterestStatus {
    PENDING,
    ACCEPTED_MATCHED,
    RESOLVED_BY_MATCH,
    DECLINED,
    INVALIDATED
}

enum class MatchStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}

enum class NotificationType {
    INCOMING_INTEREST,
    MATCH_VERIFIED,
    MATCH_CANCELLED,
    EXCHANGE_COMPLETED
}

enum class CityPreset(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
) {
    BANDUNG("Bandung, Jawa Barat", -6.9175, 107.6191),
    CIMAHI("Cimahi, Jawa Barat", -6.8722, 107.5425),
    JATINANGOR("Jatinangor, Jawa Barat", -6.9320, 107.7725),
    JAKARTA("Jakarta", -6.2088, 106.8456),
    SURABAYA("Surabaya, Jawa Timur", -7.2575, 112.7521);

    companion object {
        fun fromDisplayName(name: String): CityPreset {
            return entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: BANDUNG
        }
    }
}

object CloBleConstants {
    val SIZES = listOf("S", "M", "L", "XL")
    val GENDERS = listOf("Pria", "Wanita", "Uniseks")
    val CATEGORIES = listOf("Atasan", "Bawahan", "Luaran", "Terusan")
    val COLORS = listOf("Hitam", "Putih", "Biru", "Krem", "Hijau", "Abu-abu", "Cokelat", "Merah")
    val CONDITIONS = listOf("Sangat baik", "Baik", "Layak pakai")
    val RADII_KM = listOf(5, 10, 25, 50)
}
