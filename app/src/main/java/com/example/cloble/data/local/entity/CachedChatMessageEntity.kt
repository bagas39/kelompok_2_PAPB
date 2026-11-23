package com.example.cloble.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cached_chat_messages",
    primaryKeys = ["viewerUserId", "id"],
    indices = [
        Index(value = ["viewerUserId", "matchId", "sentAt"])
    ]
)
data class CachedChatMessageEntity(
    val viewerUserId: Long,
    val id: Long,
    val matchId: Long,
    val senderId: Long,
    val senderName: String,
    val messageText: String,
    val isRead: Boolean,
    val sentAt: Long
)
