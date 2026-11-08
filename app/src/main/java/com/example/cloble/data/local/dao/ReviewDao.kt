package com.example.cloble.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.cloble.data.local.entity.MatchEntity
import com.example.cloble.data.local.entity.ReviewEntity
import com.example.cloble.domain.model.MatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ReviewDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertReview(review: ReviewEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAllReviews(reviews: List<ReviewEntity>): List<Long>

    @Query("SELECT * FROM matches WHERE id = :matchId LIMIT 1")
    abstract suspend fun getMatchById(matchId: Long): MatchEntity?

    @Query("SELECT COUNT(*) FROM reviews WHERE matchId = :matchId AND reviewerId = :reviewerId")
    abstract suspend fun hasReviewed(matchId: Long, reviewerId: Long): Int

    @Query("SELECT COUNT(*) FROM reviews WHERE matchId = :matchId AND reviewerId = :reviewerId")
    abstract fun hasReviewedFlow(matchId: Long, reviewerId: Long): Flow<Int>

    @Query(
        """
        SELECT
            r.id AS id,
            r.matchId AS matchId,
            r.reviewerId AS reviewerId,
            u.name AS reviewerName,
            u.city AS reviewerCity,
            r.revieweeId AS revieweeId,
            r.rating AS rating,
            r.comment AS comment,
            r.createdAt AS createdAt
        FROM reviews r
        JOIN users u ON r.reviewerId = u.id
        WHERE r.revieweeId = :revieweeId
        ORDER BY r.createdAt DESC
        """
    )
    abstract fun getReviewsForUserFlow(revieweeId: Long): Flow<List<ReviewWithAuthorRow>>

    @Query(
        """
        SELECT
            r.id AS id,
            r.matchId AS matchId,
            r.reviewerId AS reviewerId,
            u.name AS reviewerName,
            u.city AS reviewerCity,
            r.revieweeId AS revieweeId,
            r.rating AS rating,
            r.comment AS comment,
            r.createdAt AS createdAt
        FROM reviews r
        JOIN users u ON r.reviewerId = u.id
        WHERE r.revieweeId = :revieweeId
        ORDER BY r.createdAt DESC
        """
    )
    abstract suspend fun getReviewsForUser(revieweeId: Long): List<ReviewWithAuthorRow>

    @Query("SELECT AVG(rating) FROM reviews WHERE revieweeId = :revieweeId")
    abstract suspend fun getAverageRatingForUser(revieweeId: Long): Double?

    @Query("SELECT COUNT(*) FROM reviews WHERE revieweeId = :revieweeId")
    abstract suspend fun getReviewCountForUser(revieweeId: Long): Int

    @Transaction
    open suspend fun submitReviewProtected(
        matchId: Long,
        reviewerId: Long,
        rating: Int,
        comment: String,
        nowMillis: Long
    ): ReviewSubmitDaoResult {
        if (rating !in 1..5) {
            return ReviewSubmitDaoResult.InvalidRating
        }
        val match = getMatchById(matchId) ?: return ReviewSubmitDaoResult.MatchNotFound
        if (reviewerId != match.user1Id && reviewerId != match.user2Id) {
            return ReviewSubmitDaoResult.NotMatchParticipant
        }
        if (match.status != MatchStatus.COMPLETED) {
            return ReviewSubmitDaoResult.MatchNotCompleted
        }
        if (hasReviewed(matchId, reviewerId) > 0) {
            return ReviewSubmitDaoResult.AlreadyReviewed
        }

        val revieweeId = if (reviewerId == match.user1Id) match.user2Id else match.user1Id
        val reviewId = insertReview(
            ReviewEntity(
                matchId = matchId,
                reviewerId = reviewerId,
                revieweeId = revieweeId,
                rating = rating,
                comment = comment.trim(),
                createdAt = nowMillis
            )
        )
        return ReviewSubmitDaoResult.Success(reviewId)
    }
}
