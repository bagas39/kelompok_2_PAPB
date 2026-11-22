package com.example.cloble.domain.model

data class UserProfile(
    val id: Long,
    val name: String,
    val email: String,
    val bio: String,
    val avatarUri: String? = null,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val preferredSize: String,
    val preferredGender: String,
    val preferredRadiusKm: Int,
    val createdAt: Long,
    val averageRating: Double = 0.0,
    val reviewCount: Int = 0
) {
    val isLowCredibility: Boolean
        get() = reviewCount >= 1 && averageRating < 3.0
}

data class UserProfileUpdate(
    val name: String,
    val bio: String,
    val avatarUri: String?,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val preferredSize: String,
    val preferredGender: String,
    val preferredRadiusKm: Int
)

data class UserPreferences(
    val preferredSize: String,
    val preferredGender: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val preferredRadiusKm: Int
)
