package com.example.cloble.data.repository

import com.example.cloble.data.local.dao.ReviewDao
import com.example.cloble.data.local.dao.ReviewSubmitDaoResult
import com.example.cloble.data.local.dao.UserDao
import com.example.cloble.domain.model.UserPreferences
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.model.UserReview
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.result.ReviewSubmitResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalProfileReviewRepository(
    private val userDao: UserDao,
    private val reviewDao: ReviewDao
) : ProfileReviewRepository {

    override fun observeUserProfile(
        userId: Long,
        viewerUserId: Long?
    ): Flow<UserProfile?> {
        return userDao.getUserByIdFlow(userId).map { user ->
            user?.let {
                val avg = reviewDao.getAverageRatingForUser(it.id) ?: 0.0
                val count = reviewDao.getReviewCountForUser(it.id)
                EntityMappers.toDomainUserProfile(it, avg, count)
            }
        }
    }

    override fun observeUserReviews(userId: Long): Flow<List<UserReview>> {
        return reviewDao.getReviewsForUserFlow(userId).map { rows ->
            rows.map { EntityMappers.toDomainReview(it) }
        }
    }

    override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> {
        return reviewDao.hasReviewedFlow(matchId, reviewerId).map { count -> count > 0 }
    }

    override suspend fun updatePreferences(userId: Long, prefs: UserPreferences) {
        userDao.updatePreferences(
            userId = userId,
            preferredSize = prefs.preferredSize,
            preferredGender = prefs.preferredGender,
            city = prefs.city,
            latitude = prefs.latitude,
            longitude = prefs.longitude,
            preferredRadiusKm = prefs.preferredRadiusKm
        )
    }

    override suspend fun updateBio(userId: Long, bio: String) {
        userDao.updateBio(userId, bio.trim())
    }

    override suspend fun submitReview(
        matchId: Long,
        reviewerId: Long,
        rating: Int,
        comment: String
    ): ReviewSubmitResult {
        val now = System.currentTimeMillis()
        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { ReviewSubmitResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { ReviewSubmitResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { ReviewSubmitResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            when (val daoResult = reviewDao.submitReviewProtected(matchId, reviewerId, rating, comment, now)) {
                is ReviewSubmitDaoResult.Success -> ReviewSubmitResult.Success(daoResult.reviewId)
                is ReviewSubmitDaoResult.MatchNotFound -> ReviewSubmitResult.MatchNotFound
                is ReviewSubmitDaoResult.NotMatchParticipant -> ReviewSubmitResult.NotMatchParticipant
                is ReviewSubmitDaoResult.MatchNotCompleted -> ReviewSubmitResult.MatchNotCompleted
                is ReviewSubmitDaoResult.AlreadyReviewed -> ReviewSubmitResult.AlreadyReviewed
                is ReviewSubmitDaoResult.InvalidRating -> ReviewSubmitResult.InvalidRating
            }
        }
    }
}
