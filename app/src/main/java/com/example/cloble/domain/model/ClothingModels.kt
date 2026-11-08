package com.example.cloble.domain.model

data class ClothingItem(
    val id: Long,
    val ownerId: Long,
    val name: String,
    val description: String,
    val category: String,
    val size: String,
    val gender: String,
    val color: String,
    val condition: String,
    val mainPhotoUri: String?,
    val detailPhotoUri: String?,
    val extraPhotoUri: String?,
    val isBarterAllowed: Boolean,
    val status: ItemStatus,
    val createdAt: Long,
    val updatedAt: Long
)

data class ClothingDraft(
    val name: String,
    val description: String,
    val category: String,
    val size: String,
    val gender: String,
    val color: String,
    val condition: String,
    val mainPhotoUri: String? = null,
    val detailPhotoUri: String? = null,
    val extraPhotoUri: String? = null,
    val isBarterAllowed: Boolean = false
)

data class WardrobeItemSummary(
    val item: ClothingItem,
    val isInActiveMatch: Boolean,
    val interestedSenderNames: List<String> = emptyList()
)

data class DiscoverCandidate(
    val item: ClothingItem,
    val ownerName: String,
    val ownerCity: String,
    val ownerAverageRating: Double,
    val ownerReviewCount: Int,
    val distanceKm: Int
)

data class DiscoverFilter(
    val selectedSizes: Set<String> = emptySet(),
    val selectedGenders: Set<String> = emptySet(),
    val selectedCategories: Set<String> = emptySet(),
    val selectedColors: Set<String> = emptySet(),
    val maxRadiusKm: Int? = null
)
