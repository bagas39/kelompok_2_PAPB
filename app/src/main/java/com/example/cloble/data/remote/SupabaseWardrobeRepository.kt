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
            val resp = client.rpc("rpc_get_my_wardrobe", JSONObject())
            if (resp.optString("code") != "SUCCESS") return emptyList()
            val arr = resp.optJSONArray("items") ?: return emptyList()
            val list = mutableListOf<WardrobeItemSummary>()
            for (i in 0 until arr.length()) {
                val entry = arr.getJSONObject(i)
                val itemJson = entry.optJSONObject("item") ?: continue
                if (itemJson.optLong("ownerId") != ownerId) continue
                val isInActiveMatch = entry.optBoolean("isInActiveMatch", false)
                val sendersArr = entry.optJSONArray("interestedSenderNames")
                val interestedSenders = mutableListOf<String>()
                if (sendersArr != null) {
                    for (j in 0 until sendersArr.length()) {
                        val name = sendersArr.optString(j).takeIf { it.isNotBlank() }
                        if (name != null) interestedSenders.add(name)
                    }
                }
                list.add(
                    SupabaseMappers.parseWardrobeItemSummary(
                        itemJson,
                        isInActiveMatch,
                        interestedSenders.distinct()
                    )
                )
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
            val resp = client.rpc(
                "rpc_get_user_public_wardrobe",
                JSONObject().put("p_owner_id", ownerId)
            )
            if (resp.optString("code") != "SUCCESS") return emptyList()
            val arr = resp.optJSONArray("items") ?: return emptyList()
            val list = mutableListOf<WardrobeItemSummary>()
            for (i in 0 until arr.length()) {
                val entry = arr.getJSONObject(i)
                val itemJson = entry.optJSONObject("item") ?: continue
                val isInActiveMatch = entry.optBoolean("isInActiveMatch", false)
                if (!isInActiveMatch) {
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
        return try {
            val resp = client.rpc("rpc_get_clothing_item", JSONObject().put("p_item_id", itemId))
            if (resp.optString("code") != "SUCCESS") return null
            val itemJson = resp.optJSONObject("item") ?: return null
            SupabaseMappers.parseClothingItem(itemJson)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun isItemLocked(itemId: Long): Boolean {
        return try {
            val resp = client.rpc("rpc_get_clothing_item", JSONObject().put("p_item_id", itemId))
            if (resp.optString("code") != "SUCCESS") return false
            resp.optBoolean("isLocked", false)
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
            val params = JSONObject().apply {
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
            }

            val resp = client.rpc("rpc_add_clothing_item", params)
            when (resp.optString("code")) {
                "SUCCESS" -> {
                    invalidator.invalidateWardrobe()
                    invalidator.invalidateDiscovery()
                    WardrobeMutationResult.Success(resp.optLong("itemId"))
                }
                "UNAUTHORIZED" -> WardrobeMutationResult.InvalidOwnership
                "VALIDATION_FAILED" -> WardrobeMutationResult.ValidationFailed(
                    resp.optString("message", "Validasi gagal")
                )
                else -> WardrobeMutationResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Gagal menambah pakaian"))
            }
        } catch (_: Exception) {
            WardrobeMutationResult.DatabaseError(
                DbFailure.UnexpectedDatabaseError("Terjadi gangguan koneksi saat menambah pakaian. Silakan coba lagi.")
            )
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
        } catch (_: Exception) {
            WardrobeMutationResult.DatabaseError(
                DbFailure.UnexpectedDatabaseError("Terjadi gangguan koneksi saat memperbarui pakaian. Silakan coba lagi.")
            )
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
        } catch (_: Exception) {
            WardrobeMutationResult.DatabaseError(
                DbFailure.UnexpectedDatabaseError("Terjadi gangguan koneksi saat mengubah status barter. Silakan coba lagi.")
            )
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
        } catch (_: Exception) {
            WardrobeMutationResult.DatabaseError(
                DbFailure.UnexpectedDatabaseError("Terjadi gangguan koneksi saat menghapus pakaian. Silakan coba lagi.")
            )
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
            if (bytes.size > MAX_PHOTO_BYTES) return@withContext null

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
            val resp = client.rpc(
                "rpc_count_eligible_barter_items",
                JSONObject().put("p_owner_id", ownerId)
            )
            if (resp.optString("code") == "SUCCESS") resp.optInt("count", 0) else 0
        } catch (_: Exception) {
            0
        }
    }

    private companion object {
        const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
    }
}
