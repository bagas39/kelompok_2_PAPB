package com.example.cloble.data.remote

import com.example.cloble.domain.model.UserPreferences
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.model.UserReview
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.result.ReviewSubmitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.json.JSONObject

class SupabaseProfileReviewRepository(
    private val client: SupabaseClient,
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
