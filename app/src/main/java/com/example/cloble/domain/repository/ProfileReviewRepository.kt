package com.example.cloble.domain.repository

import com.example.cloble.domain.model.UserPreferences
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.model.UserReview
import com.example.cloble.domain.result.ReviewSubmitResult
import kotlinx.coroutines.flow.Flow

interface ProfileReviewRepository {
    fun observeUserProfile(userId: Long, viewerUserId: Long? = null): Flow<UserProfile?>
    fun observeUserReviews(userId: Long): Flow<List<UserReview>>
    fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean>

    suspend fun updatePreferences(userId: Long, prefs: UserPreferences)
    suspend fun updateBio(userId: Long, bio: String)
    suspend fun submitReview(
        matchId: Long,
        reviewerId: Long,
        rating: Int,
        comment: String
    ): ReviewSubmitResult
}
