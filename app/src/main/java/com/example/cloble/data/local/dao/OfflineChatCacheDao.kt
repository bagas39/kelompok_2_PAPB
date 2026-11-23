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

    @Query("UPDATE cached_chat_threads SET lastMessageText = :messageText, lastMessageSentAt = :sentAt, updatedAt = :sentAt WHERE viewerUserId = :viewerUserId AND matchId = :matchId")
    abstract suspend fun updateThreadLastMessage(
        viewerUserId: Long,
        matchId: Long,
        messageText: String,
        sentAt: Long
    )

    @Query("UPDATE cached_chat_threads SET status = :status, updatedAt = :updatedAt WHERE viewerUserId = :viewerUserId AND matchId = :matchId AND status != :status")
    abstract suspend fun updateThreadStatus(
        viewerUserId: Long,
        matchId: Long,
        status: MatchStatus,
        updatedAt: Long
    )

    @Query("SELECT * FROM cached_chat_messages WHERE viewerUserId = :viewerUserId AND matchId = :matchId ORDER BY sentAt ASC, id ASC")
    abstract fun observeMessages(viewerUserId: Long, matchId: Long): Flow<List<CachedChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMessage(message: CachedChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMessages(messages: List<CachedChatMessageEntity>)

    @Query("DELETE FROM cached_chat_messages WHERE viewerUserId = :viewerUserId AND matchId = :matchId")
    abstract suspend fun deleteMessagesForMatch(viewerUserId: Long, matchId: Long)

    @Transaction
    open suspend fun replaceMessagesForMatch(
        viewerUserId: Long,
        matchId: Long,
        messages: List<CachedChatMessageEntity>
    ) {
        deleteMessagesForMatch(viewerUserId, matchId)
        if (messages.isNotEmpty()) {
            upsertMessages(messages)
        }
    }

    @Query("UPDATE cached_chat_messages SET isRead = 1 WHERE viewerUserId = :viewerUserId AND matchId = :matchId AND senderId != :viewerUserId AND isRead = 0")
    abstract suspend fun markMessagesReadLocal(viewerUserId: Long, matchId: Long)

    @Query("SELECT * FROM cached_match_details WHERE viewerUserId = :viewerUserId AND matchId = :matchId LIMIT 1")
    abstract fun observeMatchDetail(viewerUserId: Long, matchId: Long): Flow<CachedMatchDetailEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMatchDetail(detail: CachedMatchDetailEntity)

    @Query(
        """
        UPDATE cached_match_details
        SET status = :status,
            meetingLocation = COALESCE(:meetingLocation, meetingLocation),
            updatedAt = :updatedAt,
            myItem_status = CASE WHEN :status = 'COMPLETED' THEN 'EXCHANGED' ELSE myItem_status END,
            partnerItem_status = CASE WHEN :status = 'COMPLETED' THEN 'EXCHANGED' ELSE partnerItem_status END,
            myItem_isBarterAllowed = CASE WHEN :status = 'COMPLETED' THEN 0 ELSE myItem_isBarterAllowed END,
            partnerItem_isBarterAllowed = CASE WHEN :status = 'COMPLETED' THEN 0 ELSE partnerItem_isBarterAllowed END
        WHERE viewerUserId = :viewerUserId
          AND matchId = :matchId
          AND (
              status != :status
              OR (:meetingLocation IS NOT NULL AND meetingLocation IS NULL)
              OR (
                  :status = 'COMPLETED'
                  AND (
                      myItem_status != 'EXCHANGED'
                      OR partnerItem_status != 'EXCHANGED'
                      OR myItem_isBarterAllowed != 0
                      OR partnerItem_isBarterAllowed != 0
                  )
              )
          )
        """
    )
    abstract suspend fun updateMatchDetailStatus(
        viewerUserId: Long,
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
