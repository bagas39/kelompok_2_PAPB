package com.example.cloble.domain.model

data class NotificationFeedItem(
    val id: Long,
    val userId: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val relatedInterestId: Long?,
    val relatedMatchId: Long?,
    val isRead: Boolean,
    val createdAt: Long,
    val canReplyToInterest: Boolean = false,
    val disabledReasonText: String? = null,
    val counterpartUserId: Long? = null,
    val counterpartName: String? = null,
    val counterpartPhotoUri: String? = null,
    val targetItemId: Long? = null,
    val targetItemName: String? = null,
    val matchStatus: MatchStatus? = null,
    val hasReviewedMatch: Boolean = false
)

data class VerifiedMatchDetail(
    val matchId: Long,
    val status: MatchStatus,
    val meetingLocation: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val myUserId: Long,
    val partnerUserId: Long,
    val partnerName: String,
    val partnerCity: String,
    val partnerAvatarUri: String? = null,
    val partnerAverageRating: Double,
    val partnerReviewCount: Int,
    val myItem: ClothingItem,
    val partnerItem: ClothingItem
) {
    val isReadOnly: Boolean
        get() = status != MatchStatus.ACTIVE
}

data class ChatThreadSummary(
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
    val updatedAt: Long,
    val totalMatchesWithPartner: Int = 1
)
