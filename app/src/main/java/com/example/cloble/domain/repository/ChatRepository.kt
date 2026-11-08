package com.example.cloble.domain.repository

import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.result.SendMessageResult
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeChatThreads(userId: Long): Flow<List<ChatThreadSummary>>
    fun observeMessages(matchId: Long, viewerUserId: Long): Flow<List<ChatMessage>>

    suspend fun sendMessage(matchId: Long, senderId: Long, text: String): SendMessageResult
    suspend fun markMessagesRead(matchId: Long, viewerUserId: Long)
}
