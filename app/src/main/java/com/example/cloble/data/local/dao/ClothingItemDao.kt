package com.example.cloble.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.cloble.data.local.entity.ClothingItemEntity
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.ItemStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ClothingItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertClothing(item: ClothingItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<ClothingItemEntity>): List<Long>

    @Update
    abstract suspend fun updateClothing(item: ClothingItemEntity)

    @Query("SELECT * FROM clothing_items WHERE id = :itemId LIMIT 1")
    abstract suspend fun getClothingById(itemId: Long): ClothingItemEntity?

    @Query("SELECT * FROM clothing_items WHERE id = :itemId LIMIT 1")
    abstract fun getClothingByIdFlow(itemId: Long): Flow<ClothingItemEntity?>

    @Query(
        """
        SELECT COUNT(*) FROM matches
        WHERE status = 'ACTIVE' AND (user1ItemId = :itemId OR user2ItemId = :itemId)
        """
    )
    abstract suspend fun countActiveMatchesUsingItem(itemId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM clothing_items c
        WHERE c.ownerId = :userId
          AND c.status = 'AVAILABLE'
          AND c.isBarterAllowed = 1
          AND c.id NOT IN (
            SELECT user1ItemId FROM matches WHERE status = 'ACTIVE'
            UNION
            SELECT user2ItemId FROM matches WHERE status = 'ACTIVE'
          )
        """
    )
    abstract suspend fun countEligibleBarterItemsForUser(userId: Long): Int

    @Query(
        """
        SELECT c.*,
          CASE WHEN (
            SELECT COUNT(*) FROM matches m
            WHERE m.status = 'ACTIVE' AND (m.user1ItemId = c.id OR m.user2ItemId = c.id)
          ) > 0 THEN 1 ELSE 0 END AS isInActiveMatch
        FROM clothing_items c
        WHERE c.ownerId = :ownerId AND c.status != 'HIDDEN_DELETED'
        ORDER BY c.createdAt DESC
        """
    )
    abstract fun getWardrobeItemsFlow(ownerId: Long): Flow<List<WardrobeItemRow>>

    @Query(
        """
        SELECT c.*,
          CASE WHEN (
            SELECT COUNT(*) FROM matches m
            WHERE m.status = 'ACTIVE' AND (m.user1ItemId = c.id OR m.user2ItemId = c.id)
          ) > 0 THEN 1 ELSE 0 END AS isInActiveMatch
        FROM clothing_items c
        WHERE c.ownerId = :ownerId AND c.status = 'AVAILABLE' AND c.isBarterAllowed = 1
        ORDER BY c.createdAt DESC
        """
    )
    abstract fun getUserPublicWardrobeFlow(ownerId: Long): Flow<List<WardrobeItemRow>>

    @Query(
        """
        SELECT u.name FROM interests i
        JOIN users u ON i.senderUserId = u.id
        WHERE i.targetItemId = :itemId AND i.status = 'PENDING'
        ORDER BY i.createdAt ASC
        """
    )
    abstract suspend fun getInterestedSenderNamesForItem(itemId: Long): List<String>

    @Query(
        """
        UPDATE interests
        SET status = 'INVALIDATED', respondedAt = :respondedAt
        WHERE targetItemId = :targetItemId AND status = 'PENDING'
        """
    )
    abstract suspend fun invalidatePendingInterestsForTarget(targetItemId: Long, respondedAt: Long)

    @Query("UPDATE clothing_items SET status = :status, updatedAt = :updatedAt WHERE id = :itemId")
    abstract suspend fun updateItemStatus(itemId: Long, status: ItemStatus, updatedAt: Long)

    @Transaction
    open suspend fun updateClothingProtected(
        actorUserId: Long,
        itemId: Long,
        name: String,
        description: String,
        category: String,
        size: String,
        gender: String,
        color: String,
        condition: String,
        mainPhotoUri: String?,
        detailPhotoUri: String?,
        extraPhotoUri: String?,
        isBarterAllowed: Boolean,
        nowMillis: Long
    ): WardrobeMutationDaoResult {
        val existing = getClothingById(itemId) ?: return WardrobeMutationDaoResult.ItemNotFound
        if (existing.ownerId != actorUserId) {
            return WardrobeMutationDaoResult.InvalidOwnership
        }
        if (existing.status != ItemStatus.AVAILABLE) {
            return WardrobeMutationDaoResult.LockedInActiveMatch
        }
        if (countActiveMatchesUsingItem(itemId) > 0) {
            return WardrobeMutationDaoResult.LockedInActiveMatch
        }

        val updated = existing.copy(
            name = name,
            description = description,
            category = category,
            size = size,
            gender = gender,
            color = color,
            condition = condition,
            mainPhotoUri = mainPhotoUri,
            detailPhotoUri = detailPhotoUri,
            extraPhotoUri = extraPhotoUri,
            isBarterAllowed = isBarterAllowed,
            updatedAt = nowMillis
        )
        updateClothing(updated)

        if (existing.isBarterAllowed && !isBarterAllowed) {
            invalidatePendingInterestsForTarget(itemId, nowMillis)
        }

        return WardrobeMutationDaoResult.Success(itemId)
    }

    @Transaction
    open suspend fun setBarterAllowedProtected(
        actorUserId: Long,
        itemId: Long,
        allowed: Boolean,
        nowMillis: Long
    ): WardrobeMutationDaoResult {
        val existing = getClothingById(itemId) ?: return WardrobeMutationDaoResult.ItemNotFound
        if (existing.ownerId != actorUserId) {
            return WardrobeMutationDaoResult.InvalidOwnership
        }
        if (countActiveMatchesUsingItem(itemId) > 0) {
            return WardrobeMutationDaoResult.LockedInActiveMatch
        }

        val updated = existing.copy(
            isBarterAllowed = allowed,
            updatedAt = nowMillis
        )
        updateClothing(updated)

        if (existing.isBarterAllowed && !allowed) {
            invalidatePendingInterestsForTarget(itemId, nowMillis)
        }

        return WardrobeMutationDaoResult.Success(itemId)
    }

    @Transaction
    open suspend fun softDeleteClothingProtected(
        itemId: Long,
        actorUserId: Long,
        nowMillis: Long
    ): WardrobeMutationDaoResult {
        val existing = getClothingById(itemId) ?: return WardrobeMutationDaoResult.ItemNotFound
        if (existing.ownerId != actorUserId) {
            return WardrobeMutationDaoResult.InvalidOwnership
        }
        if (countActiveMatchesUsingItem(itemId) > 0) {
            return WardrobeMutationDaoResult.LockedInActiveMatch
        }

        val updated = existing.copy(
            status = ItemStatus.HIDDEN_DELETED,
            isBarterAllowed = false,
            updatedAt = nowMillis
        )
        updateClothing(updated)
        invalidatePendingInterestsForTarget(itemId, nowMillis)

        return WardrobeMutationDaoResult.Success(itemId)
    }
}
