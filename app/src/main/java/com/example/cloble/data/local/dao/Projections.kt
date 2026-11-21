package com.example.cloble.data.local.dao

import androidx.room.Embedded
import com.example.cloble.data.local.entity.ClothingItemEntity
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.NotificationType

data class WardrobeItemRow(
    @Embedded val item: ClothingItemEntity,
    val isInActiveMatch: Boolean
)

data class NotificationRow(
    val id: Long,
    val userId: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val relatedInterestId: Long?,
    val relatedMatchId: Long?,
    val isRead: Boolean,
    val createdAt: Long,
    val counterpartUserId: Long? = null,
    val counterpartName: String? = null,
    val counterpartPhotoUri: String? = null,
    val targetItemId: Long? = null,
    val targetItemName: String? = null,
    val canReplyToInterest: Boolean = false,
    val disabledReasonText: String? = null,
    val matchStatus: MatchStatus? = null,
    val hasReviewedMatch: Boolean = false
)

data class MatchWithDetailsRow(
    val matchId: Long,
    val sourceInterestId: Long,
    val user1Id: Long,
    val user1ItemId: Long,
    val user2Id: Long,
    val user2ItemId: Long,
    val status: MatchStatus,
    val meetingLocation: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val user1Name: String,
    val user1City: String,
    val user1AvatarUri: String? = null,
    val user2Name: String,
    val user2City: String,
    val user2AvatarUri: String? = null
)

data class ChatThreadRow(
    val matchId: Long,
    val partnerUserId: Long,
    val partnerName: String,
    val partnerCity: String,
    val partnerAvatarUri: String? = null,
    val partnerAverageRating: Double,
    val myItemName: String,
    val partnerItemName: String,
    val status: MatchStatus,
    val lastMessageText: String?,
    val lastMessageSentAt: Long?,
    val unreadCount: Int,
    val updatedAt: Long
)

data class ReviewWithAuthorRow(
    val id: Long,
    val matchId: Long,
    val reviewerId: Long,
    val reviewerName: String,
    val reviewerCity: String,
    val revieweeId: Long,
    val rating: Int,
    val comment: String,
    val createdAt: Long
)

data class RawDiscoverCandidate(
    @Embedded val item: ClothingItemEntity,
    val ownerName: String,
    val ownerCity: String,
    val ownerAvatarUri: String? = null,
    val ownerAverageRating: Double,
    val ownerReviewCount: Int,
    val ownerLatitude: Double,
    val ownerLongitude: Double
)

data class OutgoingInterestRow(
    val interestId: Long,
    val targetItemId: Long,
    val targetItemName: String,
    val targetItemCategory: String,
    val targetItemPhotoUri: String?,
    val targetItemStatus: ItemStatus,
    val ownerId: Long,
    val ownerName: String,
    val ownerCity: String,
    val interestStatus: com.example.cloble.domain.model.InterestStatus,
    val createdAt: Long,
    val matchId: Long?,
    val matchStatus: MatchStatus?,
    val isLockedByOtherMatch: Boolean = false
)

data class PassedClothingRow(
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
