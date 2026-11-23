package com.example.cloble.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import com.example.cloble.domain.model.MatchStatus

@Entity(
    tableName = "cached_chat_threads",
    primaryKeys = ["viewerUserId", "matchId"],
    indices = [
        Index(value = ["viewerUserId", "updatedAt"]),
        Index(value = ["viewerUserId", "partnerUserId"])
    ]
)
data class CachedChatThreadEntity(
    val viewerUserId: Long,
    val matchId: Long,
    val partnerUserId: Long,
    val partnerName: String,
    val partnerCity: String,
    val partnerAvatarUri: String? = null,
    val partnerAverageRating: Double,
    val myItemName: String,
    val partnerItemName: String,
    val status: MatchStatus,
    val lastMessageText: String? = null,
    val lastMessageSentAt: Long? = null,
    val unreadCount: Int,
    val updatedAt: Long,
    val totalMatchesWithPartner: Int = 1
)
