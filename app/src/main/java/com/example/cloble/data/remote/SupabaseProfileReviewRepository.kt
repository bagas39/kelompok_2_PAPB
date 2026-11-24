package com.example.cloble.data.remote

import android.content.Context
import android.net.Uri
import com.example.cloble.domain.model.UserPreferences
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.model.UserProfileUpdate
import com.example.cloble.domain.model.UserReview
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.result.ReviewSubmitResult
import com.example.cloble.domain.util.DistanceCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SupabaseProfileReviewRepository(
    private val client: SupabaseClient,
    private val context: Context,
    private val invalidator: SupabaseDataInvalidator,
    private val cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : ProfileReviewRepository {

    @Volatile
    private var lastMutationErrorMessage: String? = null

    fun consumeLastMutationError(): String? {
        val message = lastMutationErrorMessage
        lastMutationErrorMessage = null
        return message
    }

    override fun observeUserProfile(
        userId: Long,
        viewerUserId: Long?
    ): Flow<UserProfile?> =
        invalidator.userTrigger
            .onStart { emit(Unit) }
            .map { fetchUserProfile(userId) }
            .flowOn(Dispatchers.IO)

    private suspend fun fetchUserProfile(userId: Long): UserProfile? =
        fetchUserProfileViaRpc(client, userId)

    override fun observeUserReviews(userId: Long): Flow<List<UserReview>> =
        invalidator.userTrigger
            .onStart { emit(Unit) }
            .map { fetchUserReviews(userId) }
            .flowOn(Dispatchers.IO)

    private suspend fun fetchUserReviews(userId: Long): List<UserReview> {
        return try {
            val resp = client.rpc(
                "rpc_get_user_reviews",
                JSONObject().put("p_user_id", userId)
            )
            if (resp.optString("code") != "SUCCESS") return emptyList()
            val arr = resp.optJSONArray("reviews") ?: return emptyList()
            val list = mutableListOf<UserReview>()
            for (i in 0 until arr.length()) {
                list.add(SupabaseMappers.parseUserReview(arr.getJSONObject(i)))
            }
            list
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> =
        invalidator.userTrigger
            .onStart { emit(Unit) }
            .map {
                try {
                    val resp = client.rpc(
                        "rpc_has_user_reviewed_match",
                        JSONObject().put("p_match_id", matchId)
                    )
                    resp.optString("code") == "SUCCESS" && resp.optBoolean("hasReviewed", false)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
            }
            .flowOn(Dispatchers.IO)

    override suspend fun updatePreferences(userId: Long, prefs: UserPreferences): Boolean {
        lastMutationErrorMessage = null
        return try {
            val (effectiveLat, effectiveLon) = DistanceCalculator.resolveEffectiveCoordinates(
                city = prefs.city,
                latitude = prefs.latitude,
                longitude = prefs.longitude
            )
            val body = JSONObject().apply {
                put("p_preferred_size", prefs.preferredSize)
                put("p_preferred_gender", prefs.preferredGender)
                put("p_city", prefs.city)
                put("p_latitude", effectiveLat)
                put("p_longitude", effectiveLon)
                put("p_preferred_radius_km", prefs.preferredRadiusKm)
            }
            val resp = client.rpc("rpc_update_preferences", body)
            if (resp.optString("code") == "SUCCESS") {
                invalidator.invalidateUser()
                invalidator.invalidateDiscovery()
                true
            } else {
                lastMutationErrorMessage = if (resp.isNull("message")) {
                    null
                } else {
                    resp.optString("message", "").trim().takeIf { it.isNotEmpty() }
                }
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun updateBio(userId: Long, bio: String) {
        try {
            val body = JSONObject().apply {
                put("p_bio", bio.trim())
            }
            val resp = client.rpc("rpc_update_bio", body)
            if (resp.optString("code") == "SUCCESS") {
                invalidator.invalidateUser()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    override suspend fun updateProfile(userId: Long, update: UserProfileUpdate): Boolean {
        lastMutationErrorMessage = null
        val cleanName = update.name.trim()
        if (cleanName.isEmpty()) {
            lastMutationErrorMessage = "Nama lengkap tidak boleh kosong."
            return false
        }
        return try {
            val cleanCity = update.city.trim()
            val (effectiveLat, effectiveLon) = DistanceCalculator.resolveEffectiveCoordinates(
                city = cleanCity,
                latitude = update.latitude,
                longitude = update.longitude
            )
            val body = JSONObject().apply {
                put("p_name", cleanName)
                put("p_bio", update.bio.trim())
                put("p_avatar_uri", update.avatarUri ?: JSONObject.NULL)
                put("p_city", cleanCity)
                put("p_latitude", effectiveLat)
                put("p_longitude", effectiveLon)
                put("p_preferred_size", update.preferredSize)
                put("p_preferred_gender", update.preferredGender)
                put("p_preferred_radius_km", update.preferredRadiusKm)
            }
            val resp = client.rpc("rpc_update_profile", body)
            if (resp.optString("code") == "SUCCESS") {
                val cleanupUris = resp.extractCleanupPhotoUris()
                if (cleanupUris.isNotEmpty()) {
                    cleanupScope.launch { client.deletePhotos(cleanupUris) }
                }
                invalidator.invalidateUser()
                invalidator.invalidateDiscovery()
                true
            } else {
                lastMutationErrorMessage = if (resp.isNull("message")) {
                    null
                } else {
                    resp.optString("message", "").trim().takeIf { it.isNotEmpty() }
                }
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun persistAvatarPhoto(sourceUriString: String): String? = withContext(Dispatchers.IO) {
        if (sourceUriString.startsWith("http://") || sourceUriString.startsWith("https://")) {
            return@withContext sourceUriString
        }
        try {
            val uri = Uri.parse(sourceUriString)
            val bytes = compressAndStripExifJpeg(
                context = context,
                uri = uri,
                maxDimensionPx = MAX_AVATAR_PHOTO_DIMENSION_PX,
                maxBytes = MAX_PHOTO_BYTES
            ) ?: return@withContext null
            val uploadedUrl = client.uploadPhoto(bytes, kind = "avatar", mimeType = "image/jpeg")
            deleteCameraTempFileForUri(context, sourceUriString)
            uploadedUrl
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteUploadedPhotos(photoUris: List<String>) {
        client.deletePhotos(photoUris)
    }

    override suspend fun submitReview(
        matchId: Long,
        reviewerId: Long,
        rating: Int,
        comment: String
    ): ReviewSubmitResult {
        if (rating !in 1..5) return ReviewSubmitResult.InvalidRating

        return try {
            val params = JSONObject().apply {
                put("p_match_id", matchId)
                put("p_reviewer_id", reviewerId)
                put("p_rating", rating)
                put("p_comment", comment.trim())
            }
            val res = client.rpc("rpc_submit_review_protected", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    invalidator.invalidateUser()
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    ReviewSubmitResult.Success(res.optLong("reviewId"))
                }
                "MATCH_NOT_FOUND" -> ReviewSubmitResult.MatchNotFound
                "NOT_MATCH_PARTICIPANT" -> ReviewSubmitResult.NotMatchParticipant
                "MATCH_NOT_COMPLETED" -> ReviewSubmitResult.MatchNotCompleted
                "ALREADY_REVIEWED" -> ReviewSubmitResult.AlreadyReviewed
                "INVALID_RATING" -> ReviewSubmitResult.InvalidRating
                "VALIDATION_FAILED" -> ReviewSubmitResult.DatabaseError(
                    DbFailure.UnexpectedDatabaseError(res.optString("message", "Komentar ulasan tidak valid"))
                )
                else -> ReviewSubmitResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Gagal memberi review"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ReviewSubmitResult.DatabaseError(
                DbFailure.UnexpectedDatabaseError("Terjadi gangguan koneksi ke server. Silakan coba lagi.")
            )
        }
    }

    private companion object {
        const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
        const val MAX_AVATAR_PHOTO_DIMENSION_PX = 1024
    }
}

internal suspend fun fetchUserProfileViaRpc(client: SupabaseClient, userId: Long): UserProfile? {
    return try {
        val resp = client.rpc("rpc_get_user_profile", JSONObject().put("p_target_user_id", userId))
        if (resp.optString("code") != "SUCCESS") return null
        val userJson = resp.optJSONObject("user") ?: return null
        val avg = resp.optDouble("averageRating", 0.0)
        val count = resp.optInt("reviewCount", 0)
        SupabaseMappers.parseUserProfile(userJson, avg, count)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
