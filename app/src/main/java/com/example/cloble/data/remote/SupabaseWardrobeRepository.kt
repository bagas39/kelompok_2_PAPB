package com.example.cloble.data.remote

import android.content.Context
import android.net.Uri
import com.example.cloble.domain.model.ClothingDraft
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.ItemStatus
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

    private suspend fun fetchActiveMatchItemIds(): Set<Long> {
        val activeItemIds = mutableSetOf<Long>()
        val context = client.rpc("rpc_get_active_match_context", JSONObject())
        val itemsArr = context.optJSONArray("activeItemIds") ?: return activeItemIds
        for (i in 0 until itemsArr.length()) {
            val itemId = itemsArr.optLong(i)
            if (itemId > 0L) activeItemIds.add(itemId)
        }
        return activeItemIds
    }

    private suspend fun fetchMyWardrobe(ownerId: Long): List<WardrobeItemSummary> {
        return try {
            val itemsArr = client.getJsonArray("clothing_items?ownerId=eq.$ownerId&status=neq.HIDDEN_DELETED&order=createdAt.desc")
            val activeItemIds = fetchActiveMatchItemIds()

            val sendersByItemId = mutableMapOf<Long, MutableList<String>>()
            try {
                val pendingArr = client.getJsonArray(
                    "interests?receiverUserId=eq.$ownerId&status=eq.PENDING&select=targetItemId,users:senderUserId(name)"
                )
                for (i in 0 until pendingArr.length()) {
                    val row = pendingArr.getJSONObject(i)
                    val targetId = row.optLong("targetItemId")
                    val senderName = row.optJSONObject("users")?.optString("name")?.takeIf { it.isNotBlank() }
                    if (targetId > 0L && senderName != null) {
                        sendersByItemId.getOrPut(targetId) { mutableListOf() }.add(senderName)
                    }
                }
            } catch (_: Exception) {
            }

            val list = mutableListOf<WardrobeItemSummary>()
            for (i in 0 until itemsArr.length()) {
                val itemJson = itemsArr.getJSONObject(i)
                val itemId = itemJson.optLong("id")
                val isInActiveMatch = activeItemIds.contains(itemId)
                val interestedSenders = sendersByItemId[itemId]?.distinct() ?: emptyList()
                list.add(SupabaseMappers.parseWardrobeItemSummary(itemJson, isInActiveMatch, interestedSenders))
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
            val activeItemIds = try {
                fetchActiveMatchItemIds()
            } catch (_: Exception) {
                emptySet()
            }
            val list = mutableListOf<WardrobeItemSummary>()
            for (i in 0 until itemsArr.length()) {
                val itemJson = itemsArr.getJSONObject(i)
                val itemId = itemJson.optLong("id")
                if (!activeItemIds.contains(itemId)) {
                    list.add(SupabaseMappers.parseWardrobeItemSummary(itemJson, isInActiveMatch = false))
                }
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

    override suspend fun isItemLocked(itemId: Long): Boolean {
        return try {
            val existing = getClothingItem(itemId) ?: return false
            if (existing.status != ItemStatus.AVAILABLE) return true
            fetchActiveMatchItemIds().contains(itemId)
        } catch (_: Exception) {
            false
        }
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
            val params = JSONObject().apply {
                put("p_actor_user_id", actorUserId)
                put("p_item_id", itemId)
                put("p_name", name)
                put("p_description", draft.description.trim())
                put("p_category", draft.category)
                put("p_size", draft.size)
                put("p_gender", draft.gender)
                put("p_color", draft.color)
                put("p_condition", draft.condition)
                put("p_main_photo_uri", draft.mainPhotoUri ?: JSONObject.NULL)
                put("p_detail_photo_uri", draft.detailPhotoUri ?: JSONObject.NULL)
                put("p_extra_photo_uri", draft.extraPhotoUri ?: JSONObject.NULL)
                put("p_is_barter_allowed", draft.isBarterAllowed)
                put("p_now_millis", System.currentTimeMillis())
            }
            val res = client.rpc("rpc_update_clothing_protected", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    invalidator.invalidateWardrobe()
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    WardrobeMutationResult.Success(itemId)
                }
                "LOCKED_IN_ACTIVE_MATCH" -> WardrobeMutationResult.LockedInActiveMatch
                "INVALID_OWNERSHIP" -> WardrobeMutationResult.InvalidOwnership
                "ITEM_NOT_FOUND" -> WardrobeMutationResult.ItemNotFound
                "VALIDATION_FAILED" -> WardrobeMutationResult.ValidationFailed(
                    res.optString("message", "Validasi gagal")
                )
                else -> WardrobeMutationResult.DatabaseError(
                    DbFailure.UnexpectedDatabaseError("Gagal memperbarui pakaian")
                )
            }
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
            val params = JSONObject().apply {
                put("p_actor_user_id", actorUserId)
                put("p_item_id", itemId)
                put("p_allowed", allowed)
                put("p_now_millis", System.currentTimeMillis())
            }
            val res = client.rpc("rpc_set_barter_allowed_protected", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    invalidator.invalidateWardrobe()
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    WardrobeMutationResult.Success(itemId)
                }
                "LOCKED_IN_ACTIVE_MATCH" -> WardrobeMutationResult.LockedInActiveMatch
                "INVALID_OWNERSHIP" -> WardrobeMutationResult.InvalidOwnership
                "ITEM_NOT_FOUND" -> WardrobeMutationResult.ItemNotFound
                else -> WardrobeMutationResult.DatabaseError(
                    DbFailure.UnexpectedDatabaseError("Gagal mengubah status barter")
                )
            }
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
                    invalidator.invalidateNotifications()
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
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun countEligibleBarterItems(ownerId: Long): Int {
        return try {
            val arr = client.getJsonArray("clothing_items?ownerId=eq.$ownerId&status=eq.AVAILABLE&isBarterAllowed=eq.true&select=id")
            val activeIds = try {
                fetchActiveMatchItemIds()
            } catch (_: Exception) {
                emptySet()
            }
            var count = 0
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optLong("id")
                if (!activeIds.contains(id)) count++
            }
            count
        } catch (_: Exception) {
            0
        }
    }
}
