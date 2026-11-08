package com.example.cloble.data.repository

import com.example.cloble.data.local.dao.ChatDao
import com.example.cloble.data.local.dao.SendMessageDaoResult
import com.example.cloble.data.local.dao.UserDao
import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.result.SendMessageResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalChatRepository(
    private val chatDao: ChatDao,
    private val userDao: UserDao
) : ChatRepository {

    override fun observeChatThreads(userId: Long): Flow<List<ChatThreadSummary>> {
        return chatDao.getChatThreadsFlow(userId).map { rows ->
            rows.map { EntityMappers.toDomainChatThread(it) }
        }
    }

    override fun observeMessages(
        matchId: Long,
        viewerUserId: Long
    ): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForMatchFlow(matchId).map { list ->
            list.map { entity ->
                val senderName = userDao.getUserNameById(entity.senderId) ?: "Pengguna"
                EntityMappers.toDomainChatMessage(entity, viewerUserId, senderName)
            }
        }
    }

    override suspend fun sendMessage(
        matchId: Long,
        senderId: Long,
        text: String
    ): SendMessageResult {
        val now = System.currentTimeMillis()
        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { SendMessageResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { SendMessageResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { SendMessageResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            when (val daoResult = chatDao.sendMessageProtected(matchId, senderId, text, now)) {
                is SendMessageDaoResult.Success -> SendMessageResult.Success(daoResult.messageId)
                is SendMessageDaoResult.MatchNotFound -> SendMessageResult.MatchNotFound
                is SendMessageDaoResult.NotMatchParticipant -> SendMessageResult.NotMatchParticipant
                is SendMessageDaoResult.ChatReadOnly -> SendMessageResult.ChatReadOnly
                is SendMessageDaoResult.EmptyMessage -> SendMessageResult.EmptyMessage
            }
        }
    }

    override suspend fun markMessagesRead(matchId: Long, viewerUserId: Long) {
        chatDao.markMessagesAsRead(matchId, viewerUserId)
    }
}
