package com.example.cloble.data.repository

import android.net.Uri
import com.example.cloble.data.local.dao.ClothingItemDao
import com.example.cloble.data.local.dao.WardrobeMutationDaoResult
import com.example.cloble.data.local.entity.ClothingItemEntity
import com.example.cloble.data.local.storage.LocalPhotoStorageManager
import com.example.cloble.domain.model.ClothingDraft
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.WardrobeItemSummary
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.result.WardrobeMutationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalWardrobeRepository(
    private val clothingDao: ClothingItemDao,
    private val photoStorageManager: LocalPhotoStorageManager? = null
) : WardrobeRepository {

    override fun observeMyWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> {
        return clothingDao.getWardrobeItemsFlow(ownerId).map { rows ->
            rows.map { row ->
                val interestedSenders = clothingDao.getInterestedSenderNamesForItem(row.item.id)
                EntityMappers.toDomainWardrobeSummary(row, interestedSenders)
            }
        }
    }

    override fun observeUserPublicWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> {
        return clothingDao.getUserPublicWardrobeFlow(ownerId).map { rows ->
            rows.map { EntityMappers.toDomainWardrobeSummary(it) }
        }
    }

    override fun observeClothingItem(itemId: Long): Flow<ClothingItem?> {
        return clothingDao.getClothingByIdFlow(itemId).map { entity ->
            entity?.let { EntityMappers.toDomainClothingItem(it) }
        }
    }

    override suspend fun getClothingItem(itemId: Long): ClothingItem? {
        val entity = clothingDao.getClothingById(itemId) ?: return null
        return EntityMappers.toDomainClothingItem(entity)
    }

    override suspend fun isItemLocked(itemId: Long): Boolean {
        val entity = clothingDao.getClothingById(itemId) ?: return false
        if (entity.status != ItemStatus.AVAILABLE) return true
        return clothingDao.countActiveMatchesUsingItem(itemId) > 0
    }

    override suspend fun addClothingItem(
        ownerId: Long,
        draft: ClothingDraft
    ): WardrobeMutationResult {
        val trimmedName = draft.name.trim()
        if (trimmedName.isEmpty()) {
            return WardrobeMutationResult.ValidationFailed("Nama pakaian tidak boleh kosong")
        }

        val now = System.currentTimeMillis()
        val entity = ClothingItemEntity(
            ownerId = ownerId,
            name = trimmedName,
            description = draft.description.trim(),
            category = draft.category,
            size = draft.size,
            gender = draft.gender,
            color = draft.color,
            condition = draft.condition,
            mainPhotoUri = draft.mainPhotoUri,
            detailPhotoUri = draft.detailPhotoUri,
            extraPhotoUri = draft.extraPhotoUri,
            isBarterAllowed = draft.isBarterAllowed,
            status = ItemStatus.AVAILABLE,
            createdAt = now,
            updatedAt = now
        )

        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { WardrobeMutationResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { WardrobeMutationResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { WardrobeMutationResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            val insertedId = clothingDao.insertClothing(entity)
            WardrobeMutationResult.Success(insertedId)
        }
    }

    override suspend fun updateClothingItem(
        actorUserId: Long,
        itemId: Long,
        draft: ClothingDraft
    ): WardrobeMutationResult {
        val trimmedName = draft.name.trim()
        if (trimmedName.isEmpty()) {
            return WardrobeMutationResult.ValidationFailed("Nama pakaian tidak boleh kosong")
        }

        val now = System.currentTimeMillis()
        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { WardrobeMutationResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { WardrobeMutationResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { WardrobeMutationResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            when (
                val daoResult = clothingDao.updateClothingProtected(
                    actorUserId = actorUserId,
                    itemId = itemId,
                    name = trimmedName,
                    description = draft.description.trim(),
                    category = draft.category,
                    size = draft.size,
                    gender = draft.gender,
                    color = draft.color,
                    condition = draft.condition,
                    mainPhotoUri = draft.mainPhotoUri,
                    detailPhotoUri = draft.detailPhotoUri,
                    extraPhotoUri = draft.extraPhotoUri,
                    isBarterAllowed = draft.isBarterAllowed,
                    nowMillis = now
                )
            ) {
                is WardrobeMutationDaoResult.Success -> WardrobeMutationResult.Success(daoResult.itemId)
                is WardrobeMutationDaoResult.ItemNotFound -> WardrobeMutationResult.ItemNotFound
                is WardrobeMutationDaoResult.LockedInActiveMatch -> WardrobeMutationResult.LockedInActiveMatch
                is WardrobeMutationDaoResult.InvalidOwnership -> WardrobeMutationResult.InvalidOwnership
            }
        }
    }

    override suspend fun setBarterAllowed(
        actorUserId: Long,
        itemId: Long,
        allowed: Boolean
    ): WardrobeMutationResult {
        val now = System.currentTimeMillis()
        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { WardrobeMutationResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { WardrobeMutationResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { WardrobeMutationResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            when (val daoResult = clothingDao.setBarterAllowedProtected(actorUserId, itemId, allowed, now)) {
                is WardrobeMutationDaoResult.Success -> WardrobeMutationResult.Success(daoResult.itemId)
                is WardrobeMutationDaoResult.ItemNotFound -> WardrobeMutationResult.ItemNotFound
                is WardrobeMutationDaoResult.LockedInActiveMatch -> WardrobeMutationResult.LockedInActiveMatch
                is WardrobeMutationDaoResult.InvalidOwnership -> WardrobeMutationResult.InvalidOwnership
            }
        }
    }

    override suspend fun softDeleteClothingItem(
        actorUserId: Long,
        itemId: Long
    ): WardrobeMutationResult {
        val now = System.currentTimeMillis()
        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { WardrobeMutationResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { WardrobeMutationResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { WardrobeMutationResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            when (val daoResult = clothingDao.softDeleteClothingProtected(itemId, actorUserId, now)) {
                is WardrobeMutationDaoResult.Success -> WardrobeMutationResult.Success(daoResult.itemId)
                is WardrobeMutationDaoResult.ItemNotFound -> WardrobeMutationResult.ItemNotFound
                is WardrobeMutationDaoResult.LockedInActiveMatch -> WardrobeMutationResult.LockedInActiveMatch
                is WardrobeMutationDaoResult.InvalidOwnership -> WardrobeMutationResult.InvalidOwnership
            }
        }
    }

    override suspend fun persistLocalPhoto(sourceUriString: String): String? {
        return photoStorageManager?.let { manager ->
            try {
                val file = java.io.File(sourceUriString)
                if (file.exists() && file.isAbsolute) {
                    return@let sourceUriString
                }
                manager.savePhotoFromUri(Uri.parse(sourceUriString))
            } catch (_: Exception) {
                sourceUriString
            }
        } ?: sourceUriString
    }

    override suspend fun countEligibleBarterItems(ownerId: Long): Int {
        return clothingDao.countEligibleBarterItemsForUser(ownerId)
    }
}
