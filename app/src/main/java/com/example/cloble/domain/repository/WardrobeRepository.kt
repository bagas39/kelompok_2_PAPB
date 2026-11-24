package com.example.cloble.domain.repository

import com.example.cloble.domain.model.ClothingDraft
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.WardrobeItemSummary
import com.example.cloble.domain.result.WardrobeMutationResult
import kotlinx.coroutines.flow.Flow

interface WardrobeRepository {
    fun observeMyWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>>
    fun observeUserPublicWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>>
    fun observeClothingItem(itemId: Long): Flow<ClothingItem?>

    suspend fun getClothingItem(itemId: Long): ClothingItem?
    suspend fun isItemLocked(itemId: Long): Boolean
    suspend fun addClothingItem(ownerId: Long, draft: ClothingDraft): WardrobeMutationResult
    suspend fun updateClothingItem(actorUserId: Long, itemId: Long, draft: ClothingDraft): WardrobeMutationResult
    suspend fun setBarterAllowed(actorUserId: Long, itemId: Long, allowed: Boolean): WardrobeMutationResult
    suspend fun softDeleteClothingItem(actorUserId: Long, itemId: Long): WardrobeMutationResult
    suspend fun persistLocalPhoto(sourceUriString: String): String?
    suspend fun deleteUploadedPhotos(photoUris: List<String>) {}
    suspend fun countEligibleBarterItems(ownerId: Long): Int
}
