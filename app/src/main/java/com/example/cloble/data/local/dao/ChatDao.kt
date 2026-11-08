package com.example.cloble.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.cloble.data.local.entity.ChatMessageEntity
import com.example.cloble.data.local.entity.MatchEntity
import com.example.cloble.domain.model.MatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChatDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMessage(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAllMessages(messages: List<ChatMessageEntity>): List<Long>

    @Query("SELECT * FROM matches WHERE id = :matchId LIMIT 1")
    abstract suspend fun getMatchById(matchId: Long): MatchEntity?

    @Query("SELECT * FROM chat_messages WHERE matchId = :matchId ORDER BY sentAt ASC")
    abstract fun getMessagesForMatchFlow(matchId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE matchId = :matchId ORDER BY sentAt ASC")
    abstract suspend fun getMessagesForMatch(matchId: Long): List<ChatMessageEntity>

    @Query(
        """
        UPDATE chat_messages
        SET isRead = 1
        WHERE matchId = :matchId AND senderId != :viewerUserId AND isRead = 0
        """
    )
    abstract suspend fun markMessagesAsRead(matchId: Long, viewerUserId: Long)

    @Query("UPDATE matches SET updatedAt = :updatedAt WHERE id = :matchId")
    abstract suspend fun updateMatchUpdatedAt(matchId: Long, updatedAt: Long)

    @Query(
        """
        SELECT
            m.id AS matchId,
            CASE WHEN m.user1Id = :userId THEN m.user2Id ELSE m.user1Id END AS partnerUserId,
            CASE WHEN m.user1Id = :userId THEN u2.name ELSE u1.name END AS partnerName,
            CASE WHEN m.user1Id = :userId THEN u2.city ELSE u1.city END AS partnerCity,
            COALESCE((
                SELECT AVG(r.rating) FROM reviews r
                WHERE r.revieweeId = (CASE WHEN m.user1Id = :userId THEN m.user2Id ELSE m.user1Id END)
            ), 0.0) AS partnerAverageRating,
            CASE WHEN m.user1Id = :userId THEN c1.name ELSE c2.name END AS myItemName,
            CASE WHEN m.user1Id = :userId THEN c2.name ELSE c1.name END AS partnerItemName,
            m.status AS status,
            (
                SELECT cm.messageText FROM chat_messages cm
                WHERE cm.matchId = m.id
                ORDER BY cm.sentAt DESC LIMIT 1
            ) AS lastMessageText,
            (
                SELECT cm.sentAt FROM chat_messages cm
                WHERE cm.matchId = m.id
                ORDER BY cm.sentAt DESC LIMIT 1
            ) AS lastMessageSentAt,
            (
                SELECT COUNT(cm.id) FROM chat_messages cm
                WHERE cm.matchId = m.id AND cm.senderId != :userId AND cm.isRead = 0
            ) AS unreadCount,
            m.updatedAt AS updatedAt
        FROM matches m
        JOIN users u1 ON m.user1Id = u1.id
        JOIN users u2 ON m.user2Id = u2.id
        JOIN clothing_items c1 ON m.user1ItemId = c1.id
        JOIN clothing_items c2 ON m.user2ItemId = c2.id
        WHERE m.user1Id = :userId OR m.user2Id = :userId
        ORDER BY m.updatedAt DESC
        """
    )
    abstract fun getChatThreadsFlow(userId: Long): Flow<List<ChatThreadRow>>

    @Transaction
    open suspend fun sendMessageProtected(
        matchId: Long,
        senderId: Long,
        messageText: String,
        nowMillis: Long
    ): SendMessageDaoResult {
        val match = getMatchById(matchId) ?: return SendMessageDaoResult.MatchNotFound
        if (senderId != match.user1Id && senderId != match.user2Id) {
            return SendMessageDaoResult.NotMatchParticipant
        }
        if (match.status != MatchStatus.ACTIVE) {
            return SendMessageDaoResult.ChatReadOnly
        }
        val trimmed = messageText.trim()
        if (trimmed.isEmpty()) {
            return SendMessageDaoResult.EmptyMessage
        }

        val msgId = insertMessage(
            ChatMessageEntity(
                matchId = matchId,
                senderId = senderId,
                messageText = trimmed,
                isRead = false,
                sentAt = nowMillis
            )
        )
        updateMatchUpdatedAt(matchId, nowMillis)
        return SendMessageDaoResult.Success(msgId)
    }
}
