package com.example.cloble.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_chat_messages",
    indices = [
        Index(value = ["matchId", "sentAt"])
    ]
)
data class CachedChatMessageEntity(
    @PrimaryKey val id: Long,
    val matchId: Long,
    val senderId: Long,
    val senderName: String,
    val messageText: String,
    val isRead: Boolean,
    val sentAt: Long
)
