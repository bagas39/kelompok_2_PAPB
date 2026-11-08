package com.example.cloble.domain.model

data class ChatMessage(
    val id: Long,
    val matchId: Long,
    val senderId: Long,
    val senderName: String,
    val messageText: String,
    val isRead: Boolean,
    val sentAt: Long,
    val isFromMe: Boolean = false
)

data class UserReview(
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
