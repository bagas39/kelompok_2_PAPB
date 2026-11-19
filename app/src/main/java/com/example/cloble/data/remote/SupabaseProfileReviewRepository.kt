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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class SupabaseProfileReviewRepository(
    private val client: SupabaseClient,
    private val context: Context,
    private val invalidator: SupabaseDataInvalidator
) : ProfileReviewRepository {

    override fun observeUserProfile(
        userId: Long,
        viewerUserId: Long?
    ): Flow<UserProfile?> =
        invalidator.userTrigger
            .onStart { emit(Unit) }
            .map { fetchUserProfile(userId) }
            .flowOn(Dispatchers.IO)

    private suspend fun fetchUserProfile(userId: Long): UserProfile? {
        return try {
            val userJson = client.getJsonObjectOrNull("users?id=eq.$userId") ?: return null
            val reviewsArr = client.getJsonArray("reviews?revieweeId=eq.$userId&select=rating")
            var sum = 0.0
            for (i in 0 until reviewsArr.length()) {
                sum += reviewsArr.getJSONObject(i).optDouble("rating", 0.0)
            }
            val count = reviewsArr.length()
            val avg = if (count > 0) sum / count else 0.0
            SupabaseMappers.parseUserProfile(userJson, avg, count)
        } catch (_: Exception) {
            null
        }
    }

    override fun observeUserReviews(userId: Long): Flow<List<UserReview>> =
        invalidator.userTrigger
            .onStart { emit(Unit) }
            .map { fetchUserReviews(userId) }
            .flowOn(Dispatchers.IO)

    private suspend fun fetchUserReviews(userId: Long): List<UserReview> {
        return try {
            val arr = client.getJsonArray(
                "reviews?revieweeId=eq.$userId&select=*,users:reviewerId(name,city)&order=createdAt.desc"
            )
            val list = mutableListOf<UserReview>()
            for (i in 0 until arr.length()) {
                list.add(SupabaseMappers.parseUserReview(arr.getJSONObject(i)))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> =
        invalidator.userTrigger
            .onStart { emit(Unit) }
            .map {
                try {
                    val arr = client.getJsonArray("reviews?matchId=eq.$matchId&reviewerId=eq.$reviewerId&select=id")
                    arr.length() > 0
                } catch (_: Exception) {
                    false
                }
            }
            .flowOn(Dispatchers.IO)

    override suspend fun updatePreferences(userId: Long, prefs: UserPreferences) {
        try {
            val body = JSONObject().apply {
                put("preferredSize", prefs.preferredSize)
                put("preferredGender", prefs.preferredGender)
                put("city", prefs.city)
                put("latitude", prefs.latitude)
                put("longitude", prefs.longitude)
                put("preferredRadiusKm", prefs.preferredRadiusKm)
            }
            client.patch("users?id=eq.$userId", body.toString())
            invalidator.invalidateUser()
            invalidator.invalidateDiscovery()
        } catch (_: Exception) {
        }
    }

    override suspend fun updateBio(userId: Long, bio: String) {
        try {
            val body = JSONObject().apply {
                put("bio", bio.trim())
            }
            client.patch("users?id=eq.$userId", body.toString())
            invalidator.invalidateUser()
        } catch (_: Exception) {
        }
    }

    override suspend fun updateProfile(userId: Long, update: UserProfileUpdate): Boolean {
        val cleanName = update.name.trim()
        if (cleanName.isEmpty()) return false
        return try {
            val body = JSONObject().apply {
                put("name", cleanName)
                put("bio", update.bio.trim())
                put("avatarUri", update.avatarUri ?: JSONObject.NULL)
                put("city", update.city.trim())
                put("latitude", update.latitude)
                put("longitude", update.longitude)
                put("preferredSize", update.preferredSize)
                put("preferredGender", update.preferredGender)
                put("preferredRadiusKm", update.preferredRadiusKm)
            }
            client.patch("users?id=eq.$userId", body.toString())
            invalidator.invalidateUser()
            invalidator.invalidateDiscovery()
            true
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
            val filename = "avatar_${UUID.randomUUID()}.$ext"
            client.uploadPhoto(bytes, filename, mime)
        } catch (_: Exception) {
            null
        }
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
                put("p_now_millis", System.currentTimeMillis())
            }
            val res = client.rpc("rpc_submit_review_protected", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    invalidator.invalidateUser()
                    invalidator.invalidateDiscovery()
                    ReviewSubmitResult.Success(res.optLong("reviewId"))
                }
                "MATCH_NOT_FOUND" -> ReviewSubmitResult.MatchNotFound
                "NOT_MATCH_PARTICIPANT" -> ReviewSubmitResult.NotMatchParticipant
                "MATCH_NOT_COMPLETED" -> ReviewSubmitResult.MatchNotCompleted
                "ALREADY_REVIEWED" -> ReviewSubmitResult.AlreadyReviewed
                "INVALID_RATING" -> ReviewSubmitResult.InvalidRating
                else -> ReviewSubmitResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Gagal memberi review"))
            }
        } catch (e: Exception) {
            ReviewSubmitResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
        }
    }
}
