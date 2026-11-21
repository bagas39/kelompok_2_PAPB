package com.example.cloble.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus

data class CachedClothingSnapshot(
    val id: Long,
    val ownerId: Long,
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
    val isBarterAllowed: Boolean,
    val status: ItemStatus,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "cached_match_details",
    primaryKeys = ["viewerUserId", "matchId"]
)
data class CachedMatchDetailEntity(
    val viewerUserId: Long,
    val matchId: Long,
    val status: MatchStatus,
    val meetingLocation: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val partnerUserId: Long,
    val partnerName: String,
    val partnerCity: String,
    val partnerAvatarUri: String? = null,
    val partnerAverageRating: Double,
    val partnerReviewCount: Int,
    @Embedded(prefix = "myItem_") val myItem: CachedClothingSnapshot,
    @Embedded(prefix = "partnerItem_") val partnerItem: CachedClothingSnapshot
)
