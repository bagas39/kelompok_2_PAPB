package com.example.cloble.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cloble.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNotification(notification: NotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<NotificationEntity>): List<Long>

    @Query(
        """
        SELECT
            n.id AS id,
            n.userId AS userId,
            n.type AS type,
            n.title AS title,
            n.body AS body,
            n.relatedInterestId AS relatedInterestId,
            n.relatedMatchId AS relatedMatchId,
            n.isRead AS isRead,
            n.createdAt AS createdAt,
            sender.id AS counterpartUserId,
            sender.name AS counterpartName,
            item.mainPhotoUri AS counterpartPhotoUri,
            item.id AS targetItemId,
            item.name AS targetItemName,
            CASE WHEN (
              n.type = 'INCOMING_INTEREST'
              AND i.status = 'PENDING'
              AND item.status = 'AVAILABLE'
              AND item.isBarterAllowed = 1
              AND (SELECT COUNT(*) FROM matches m WHERE m.status = 'ACTIVE' AND (m.user1ItemId = item.id OR m.user2ItemId = item.id)) = 0
            ) THEN 1 ELSE 0 END AS canReplyToInterest,
            CASE
              WHEN i.status = 'ACCEPTED_MATCHED' THEN 'Sudah terhubung dalam Match'
              WHEN i.status = 'RESOLVED_BY_MATCH' THEN 'Diselesaikan melalui Match pasangan'
              WHEN i.status = 'DECLINED' THEN 'Tawaran telah ditolak'
              WHEN i.status = 'INVALIDATED' THEN 'Pakaian sudah tidak tersedia'
              ELSE NULL
            END AS disabledReasonText,
            relMatch.status AS matchStatus,
            CASE WHEN n.relatedMatchId IS NOT NULL AND EXISTS (
              SELECT 1 FROM reviews r WHERE r.matchId = n.relatedMatchId AND r.reviewerId = :userId
            ) THEN 1 ELSE 0 END AS hasReviewedMatch
        FROM notifications n
        LEFT JOIN interests i ON n.relatedInterestId = i.id
        LEFT JOIN users sender ON i.senderUserId = sender.id
        LEFT JOIN clothing_items item ON i.targetItemId = item.id
        LEFT JOIN matches relMatch ON n.relatedMatchId = relMatch.id
        WHERE n.userId = :userId
        ORDER BY n.createdAt DESC
        """
    )
    fun getNotificationsFlow(userId: Long): Flow<List<NotificationRow>>

    @Query(
        """
        SELECT
            n.id AS id,
            n.userId AS userId,
            n.type AS type,
            n.title AS title,
            n.body AS body,
            n.relatedInterestId AS relatedInterestId,
            n.relatedMatchId AS relatedMatchId,
            n.isRead AS isRead,
            n.createdAt AS createdAt,
            sender.id AS counterpartUserId,
            sender.name AS counterpartName,
            item.mainPhotoUri AS counterpartPhotoUri,
            item.id AS targetItemId,
            item.name AS targetItemName,
            CASE WHEN (
              n.type = 'INCOMING_INTEREST'
              AND i.status = 'PENDING'
              AND item.status = 'AVAILABLE'
              AND item.isBarterAllowed = 1
              AND (SELECT COUNT(*) FROM matches m WHERE m.status = 'ACTIVE' AND (m.user1ItemId = item.id OR m.user2ItemId = item.id)) = 0
            ) THEN 1 ELSE 0 END AS canReplyToInterest,
            CASE
              WHEN i.status = 'ACCEPTED_MATCHED' THEN 'Sudah terhubung dalam Match'
              WHEN i.status = 'RESOLVED_BY_MATCH' THEN 'Diselesaikan melalui Match pasangan'
              WHEN i.status = 'DECLINED' THEN 'Tawaran telah ditolak'
              WHEN i.status = 'INVALIDATED' THEN 'Pakaian sudah tidak tersedia'
              ELSE NULL
            END AS disabledReasonText,
            relMatch.status AS matchStatus,
            CASE WHEN n.relatedMatchId IS NOT NULL AND EXISTS (
              SELECT 1 FROM reviews r WHERE r.matchId = n.relatedMatchId AND r.reviewerId = :userId
            ) THEN 1 ELSE 0 END AS hasReviewedMatch
        FROM notifications n
        LEFT JOIN interests i ON n.relatedInterestId = i.id
        LEFT JOIN users sender ON i.senderUserId = sender.id
        LEFT JOIN clothing_items item ON i.targetItemId = item.id
        LEFT JOIN matches relMatch ON n.relatedMatchId = relMatch.id
        WHERE n.userId = :userId
        ORDER BY n.createdAt DESC
        """
    )
    suspend fun getNotifications(userId: Long): List<NotificationRow>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    fun getUnreadNotificationCount(userId: Long): Flow<Int>

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId AND isRead = 0")
    suspend fun markNotificationsRead(userId: Long)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :notificationId")
    suspend fun markNotificationReadById(notificationId: Long)
}
