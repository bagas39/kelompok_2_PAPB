package com.example.cloble.domain.model

enum class ActivityHistoryTab {
    TERTARIK,
    DILEWATI
}

data class OutgoingInterestSummary(
    val interestId: Long,
    val targetItemId: Long,
    val targetItemName: String,
    val targetItemCategory: String,
    val targetItemPhotoUri: String?,
    val targetItemStatus: ItemStatus,
    val ownerId: Long,
    val ownerName: String,
    val ownerCity: String,
    val interestStatus: InterestStatus,
    val createdAt: Long,
    val matchId: Long?,
    val matchStatus: MatchStatus?,
    val isLockedByOtherMatch: Boolean = false
)

data class PassedClothingSummary(
    val swipePassId: Long,
    val clothingId: Long,
    val clothingName: String,
    val clothingCategory: String,
    val clothingPhotoUri: String?,
    val clothingStatus: ItemStatus,
    val isBarterAllowed: Boolean,
    val ownerId: Long,
    val ownerName: String,
    val ownerCity: String,
    val passedAt: Long,
    val isInActiveMatch: Boolean = false
)
