package com.example.cloble.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.cloble.data.local.entity.CachedChatMessageEntity
import com.example.cloble.data.local.entity.CachedChatThreadEntity
import com.example.cloble.data.local.entity.CachedMatchDetailEntity
import com.example.cloble.domain.model.MatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class OfflineChatCacheDao {

    @Query("SELECT * FROM cached_chat_threads WHERE viewerUserId = :viewerUserId ORDER BY updatedAt DESC, matchId DESC")
    abstract fun observeThreads(viewerUserId: Long): Flow<List<CachedChatThreadEntity>>

    @Query("SELECT * FROM cached_chat_threads WHERE viewerUserId = :viewerUserId AND partnerUserId = :partnerUserId ORDER BY updatedAt DESC, matchId DESC")
    abstract fun observePartnerThreads(viewerUserId: Long, partnerUserId: Long): Flow<List<CachedChatThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertThreads(threads: List<CachedChatThreadEntity>)

    @Query("DELETE FROM cached_chat_threads WHERE viewerUserId = :viewerUserId")
    abstract suspend fun deleteThreadsForUser(viewerUserId: Long)

    @Query("DELETE FROM cached_chat_threads WHERE viewerUserId = :viewerUserId AND partnerUserId = :partnerUserId")
    abstract suspend fun deletePartnerThreadsForUser(viewerUserId: Long, partnerUserId: Long)

    @Transaction
    open suspend fun replaceThreadsForUser(viewerUserId: Long, threads: List<CachedChatThreadEntity>) {
        deleteThreadsForUser(viewerUserId)
        if (threads.isNotEmpty()) {
            upsertThreads(threads)
        }
    }

    @Transaction
    open suspend fun replacePartnerThreadsForUser(
        viewerUserId: Long,
        partnerUserId: Long,
        threads: List<CachedChatThreadEntity>
    ) {
        deletePartnerThreadsForUser(viewerUserId, partnerUserId)
        if (threads.isNotEmpty()) {
            upsertThreads(threads)
        }
    }

    @Query("UPDATE cached_chat_threads SET unreadCount = 0 WHERE viewerUserId = :viewerUserId AND matchId = :matchId")
    abstract suspend fun clearThreadUnreadCount(viewerUserId: Long, matchId: Long)

    @Query("UPDATE cached_chat_threads SET lastMessageText = :messageText, lastMessageSentAt = :sentAt, updatedAt = :sentAt WHERE matchId = :matchId")
    abstract suspend fun updateThreadLastMessage(matchId: Long, messageText: String, sentAt: Long)

    @Query("UPDATE cached_chat_threads SET status = :status, updatedAt = :updatedAt WHERE matchId = :matchId AND status != :status")
    abstract suspend fun updateThreadStatus(matchId: Long, status: MatchStatus, updatedAt: Long)

    @Query("SELECT * FROM cached_chat_messages WHERE matchId = :matchId ORDER BY sentAt ASC, id ASC")
    abstract fun observeMessages(matchId: Long): Flow<List<CachedChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMessage(message: CachedChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMessages(messages: List<CachedChatMessageEntity>)

    @Query("UPDATE cached_chat_messages SET isRead = 1 WHERE matchId = :matchId AND senderId != :viewerUserId AND isRead = 0")
    abstract suspend fun markMessagesReadLocal(matchId: Long, viewerUserId: Long)

    @Query("SELECT * FROM cached_match_details WHERE viewerUserId = :viewerUserId AND matchId = :matchId LIMIT 1")
    abstract fun observeMatchDetail(viewerUserId: Long, matchId: Long): Flow<CachedMatchDetailEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMatchDetail(detail: CachedMatchDetailEntity)

    @Query(
        """
        UPDATE cached_match_details
        SET status = :status,
            meetingLocation = COALESCE(:meetingLocation, meetingLocation),
            updatedAt = :updatedAt
        WHERE matchId = :matchId
          AND (status != :status OR (:meetingLocation IS NOT NULL AND meetingLocation IS NULL))
        """
    )
    abstract suspend fun updateMatchDetailStatus(
        matchId: Long,
        status: MatchStatus,
        meetingLocation: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM cached_match_details WHERE viewerUserId = :viewerUserId AND matchId = :matchId")
    abstract suspend fun deleteMatchDetail(viewerUserId: Long, matchId: Long)

    @Query("DELETE FROM cached_chat_threads")
    abstract suspend fun clearAllThreads()

    @Query("DELETE FROM cached_chat_messages")
    abstract suspend fun clearAllMessages()

    @Query("DELETE FROM cached_match_details")
    abstract suspend fun clearAllMatchDetails()

    @Transaction
    open suspend fun clearAll() {
        clearAllThreads()
        clearAllMessages()
        clearAllMatchDetails()
    }
}
