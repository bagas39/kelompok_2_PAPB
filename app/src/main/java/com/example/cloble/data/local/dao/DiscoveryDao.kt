package com.example.cloble.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cloble.data.local.entity.SwipePassEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscoveryDao {

    @Query(
        """
        SELECT
            c.*,
            u.name AS ownerName,
            u.city AS ownerCity,
            u.avatarUri AS ownerAvatarUri,
            COALESCE((SELECT AVG(r.rating) FROM reviews r WHERE r.revieweeId = u.id), 0.0) AS ownerAverageRating,
            COALESCE((SELECT COUNT(r.id) FROM reviews r WHERE r.revieweeId = u.id), 0) AS ownerReviewCount,
            u.latitude AS ownerLatitude,
            u.longitude AS ownerLongitude
        FROM clothing_items c
        JOIN users u ON c.ownerId = u.id
        WHERE c.ownerId != :viewerUserId
          AND c.status = 'AVAILABLE'
          AND c.isBarterAllowed = 1
          AND c.id NOT IN (
            SELECT user1ItemId FROM matches WHERE status = 'ACTIVE'
            UNION
            SELECT user2ItemId FROM matches WHERE status = 'ACTIVE'
          )
          AND c.id NOT IN (
            SELECT targetItemId FROM swipe_passes WHERE userId = :viewerUserId
          )
          AND NOT EXISTS (
            SELECT 1 FROM interests i WHERE i.senderUserId = :viewerUserId AND i.targetItemId = c.id
          )
        ORDER BY c.createdAt DESC
        """
    )
    fun getDiscoverCandidatesFlow(viewerUserId: Long): Flow<List<RawDiscoverCandidate>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSwipePass(pass: SwipePassEntity): Long

    @Query("DELETE FROM swipe_passes WHERE userId = :userId")
    suspend fun clearSwipePassesForUser(userId: Long)

    @Query(
        """
        SELECT
            sp.id AS swipePassId,
            c.id AS clothingId,
            c.name AS clothingName,
            c.category AS clothingCategory,
            c.mainPhotoUri AS clothingPhotoUri,
            c.status AS clothingStatus,
            c.isBarterAllowed AS isBarterAllowed,
            u.id AS ownerId,
            u.name AS ownerName,
            u.city AS ownerCity,
            sp.createdAt AS passedAt,
            CASE WHEN (
              SELECT COUNT(*) FROM matches m
              WHERE m.status = 'ACTIVE' AND (m.user1ItemId = c.id OR m.user2ItemId = c.id)
            ) > 0 THEN 1 ELSE 0 END AS isInActiveMatch
        FROM swipe_passes sp
        JOIN clothing_items c ON sp.targetItemId = c.id
        JOIN users u ON c.ownerId = u.id
        WHERE sp.userId = :userId
        ORDER BY sp.createdAt DESC
        """
    )
    fun getPassedClothingFlow(userId: Long): Flow<List<PassedClothingRow>>

    @Query("DELETE FROM swipe_passes WHERE userId = :userId AND targetItemId = :targetItemId")
    suspend fun deleteSwipePass(userId: Long, targetItemId: Long): Int

    @Query("SELECT COUNT(*) FROM swipe_passes WHERE userId = :userId AND targetItemId = :targetItemId")
    suspend fun hasUserPassedItem(userId: Long, targetItemId: Long): Int

    @Query("SELECT COUNT(*) FROM swipe_passes WHERE userId = :userId")
    suspend fun countSwipePassesForUser(userId: Long): Int
}
