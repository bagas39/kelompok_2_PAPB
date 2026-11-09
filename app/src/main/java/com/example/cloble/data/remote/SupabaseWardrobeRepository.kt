package com.example.cloble.data.remote

import android.content.Context
import android.net.Uri
import com.example.cloble.domain.model.ClothingDraft
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.WardrobeItemSummary
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.result.WardrobeMutationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SupabaseWardrobeRepository(
    private val client: SupabaseClient,
    private val context: Context,
    private val invalidator: SupabaseDataInvalidator
) : WardrobeRepository {

    override fun observeMyWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> =
        invalidator.wardrobeTrigger
            .onStart { emit(Unit) }
            .map { fetchMyWardrobe(ownerId) }
            .flowOn(Dispatchers.IO)

    private suspend fun fetchMyWardrobe(ownerId: Long): List<WardrobeItemSummary> {
        return try {
            val itemsArr = client.getJsonArray("clothing_items?ownerId=eq.$ownerId&status=neq.HIDDEN_DELETED&order=createdAt.desc")
            val activeMatchesArr = client.getJsonArray("matches?status=eq.ACTIVE&select=user1ItemId,user2ItemId")
            val activeItemIds = mutableSetOf<Long>()
            for (i in 0 until activeMatchesArr.length()) {
                val m = activeMatchesArr.getJSONObject(i)
                activeItemIds.add(m.optLong("user1ItemId"))
                activeItemIds.add(m.optLong("user2ItemId"))
            }

            val list = mutableListOf<WardrobeItemSummary>()
            for (i in 0 until itemsArr.length()) {
                val itemJson = itemsArr.getJSONObject(i)
                val itemId = itemJson.optLong("id")
                val isInActiveMatch = activeItemIds.contains(itemId)
                list.add(SupabaseMappers.parseWardrobeItemSummary(itemJson, isInActiveMatch))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun observeUserPublicWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> =
        invalidator.wardrobeTrigger
            .onStart { emit(Unit) }
            .map { fetchUserPublicWardrobe(ownerId) }
            .flowOn(Dispatchers.IO)

    private suspend fun fetchUserPublicWardrobe(ownerId: Long): List<WardrobeItemSummary> {
        return try {
            val itemsArr = client.getJsonArray("clothing_items?ownerId=eq.$ownerId&status=eq.AVAILABLE&isBarterAllowed=eq.true&order=createdAt.desc")
            val list = mutableListOf<WardrobeItemSummary>()
            for (i in 0 until itemsArr.length()) {
                list.add(SupabaseMappers.parseWardrobeItemSummary(itemsArr.getJSONObject(i)))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun observeClothingItem(itemId: Long): Flow<ClothingItem?> =
        invalidator.wardrobeTrigger
            .onStart { emit(Unit) }
            .map { getClothingItem(itemId) }
            .flowOn(Dispatchers.IO)

    override suspend fun getClothingItem(itemId: Long): ClothingItem? {
        val json = client.getJsonObjectOrNull("clothing_items?id=eq.$itemId") ?: return null
        return SupabaseMappers.parseClothingItem(json)
    }

    override suspend fun addClothingItem(
        ownerId: Long,
        draft: ClothingDraft
    ): WardrobeMutationResult {
        val name = draft.name.trim()
        if (name.isEmpty()) return WardrobeMutationResult.ValidationFailed("Nama pakaian tidak boleh kosong")

        return try {
            val now = System.currentTimeMillis()
            val body = JSONObject().apply {
                put("ownerId", ownerId)
                put("name", name)
                put("description", draft.description.trim())
                put("category", draft.category)
                put("size", draft.size)
                put("gender", draft.gender)
                put("color", draft.color)
                put("condition", draft.condition)
                put("mainPhotoUri", draft.mainPhotoUri ?: JSONObject.NULL)
                put("detailPhotoUri", draft.detailPhotoUri ?: JSONObject.NULL)
                put("extraPhotoUri", draft.extraPhotoUri ?: JSONObject.NULL)
                put("isBarterAllowed", draft.isBarterAllowed)
                put("status", "AVAILABLE")
                put("createdAt", now)
                put("updatedAt", now)
            }

            val resp = client.post("clothing_items", body.toString(), returnRepresentation = true)
            val createdArr = JSONArray(resp)
            if (createdArr.length() > 0) {
                invalidator.invalidateWardrobe()
                invalidator.invalidateDiscovery()
                WardrobeMutationResult.Success(createdArr.getJSONObject(0).optLong("id"))
            } else {
                WardrobeMutationResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Gagal menambah pakaian"))
            }
        } catch (e: Exception) {
            WardrobeMutationResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
        }
    }

    override suspend fun updateClothingItem(
        actorUserId: Long,
        itemId: Long,
        draft: ClothingDraft
    ): WardrobeMutationResult {
        val name = draft.name.trim()
        if (name.isEmpty()) return WardrobeMutationResult.ValidationFailed("Nama pakaian tidak boleh kosong")

        return try {
            val existing = getClothingItem(itemId) ?: return WardrobeMutationResult.ItemNotFound
            if (existing.ownerId != actorUserId) return WardrobeMutationResult.InvalidOwnership

            val now = System.currentTimeMillis()
            val body = JSONObject().apply {
                put("name", name)
                put("description", draft.description.trim())
                put("category", draft.category)
                put("size", draft.size)
                put("gender", draft.gender)
                put("color", draft.color)
                put("condition", draft.condition)
                put("mainPhotoUri", draft.mainPhotoUri ?: JSONObject.NULL)
                put("detailPhotoUri", draft.detailPhotoUri ?: JSONObject.NULL)
                put("extraPhotoUri", draft.extraPhotoUri ?: JSONObject.NULL)
                put("isBarterAllowed", draft.isBarterAllowed)
                put("updatedAt", now)
            }

            client.patch("clothing_items?id=eq.$itemId", body.toString())
            invalidator.invalidateWardrobe()
            invalidator.invalidateDiscovery()
            WardrobeMutationResult.Success(itemId)
        } catch (e: Exception) {
            WardrobeMutationResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
        }
    }

    override suspend fun setBarterAllowed(
        actorUserId: Long,
        itemId: Long,
        allowed: Boolean
    ): WardrobeMutationResult {
        return try {
            val existing = getClothingItem(itemId) ?: return WardrobeMutationResult.ItemNotFound
            if (existing.ownerId != actorUserId) return WardrobeMutationResult.InvalidOwnership

            val body = JSONObject().apply {
                put("isBarterAllowed", allowed)
                put("updatedAt", System.currentTimeMillis())
            }
            client.patch("clothing_items?id=eq.$itemId", body.toString())
            invalidator.invalidateWardrobe()
            invalidator.invalidateDiscovery()
            WardrobeMutationResult.Success(itemId)
        } catch (e: Exception) {
            WardrobeMutationResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
        }
    }

    override suspend fun softDeleteClothingItem(
        actorUserId: Long,
        itemId: Long
    ): WardrobeMutationResult {
        return try {
            val params = JSONObject().apply {
                put("p_item_id", itemId)
                put("p_actor_user_id", actorUserId)
                put("p_now_millis", System.currentTimeMillis())
            }
            val res = client.rpc("rpc_soft_delete_clothing_protected", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    invalidator.invalidateWardrobe()
                    invalidator.invalidateDiscovery()
                    WardrobeMutationResult.Success(itemId)
                }
                "INVALID_OWNERSHIP" -> WardrobeMutationResult.InvalidOwnership
                "LOCKED_IN_ACTIVE_MATCH" -> WardrobeMutationResult.LockedInActiveMatch
                else -> WardrobeMutationResult.ItemNotFound
            }
        } catch (e: Exception) {
            WardrobeMutationResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
        }
    }

    override suspend fun persistLocalPhoto(sourceUriString: String): String? = withContext(Dispatchers.IO) {
        if (sourceUriString.startsWith("http://") || sourceUriString.startsWith("https://")) {
            return@withContext sourceUriString
        }

        try {
            val uri = Uri.parse(sourceUriString)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null

            val ext = when (context.contentResolver.getType(uri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val mime = when (ext) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }

            val filename = "photo_${UUID.randomUUID()}.$ext"
            client.uploadPhoto(bytes, filename, mime)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun countEligibleBarterItems(ownerId: Long): Int {
        return try {
            val arr = client.getJsonArray("clothing_items?ownerId=eq.$ownerId&status=eq.AVAILABLE&isBarterAllowed=eq.true&select=id")
            arr.length()
        } catch (_: Exception) {
            0
        }
    }
}
