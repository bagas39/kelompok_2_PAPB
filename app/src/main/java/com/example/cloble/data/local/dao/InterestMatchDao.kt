package com.example.cloble.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.cloble.data.local.entity.ClothingItemEntity
import com.example.cloble.data.local.entity.InterestEntity
import com.example.cloble.data.local.entity.MatchEntity
import com.example.cloble.data.local.entity.NotificationEntity
import com.example.cloble.data.local.entity.UserEntity
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.NotificationType
import kotlinx.coroutines.flow.Flow

@Dao
abstract class InterestMatchDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertInterest(interest: InterestEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAllInterests(interests: List<InterestEntity>): List<Long>

    @Update
    abstract suspend fun updateInterest(interest: InterestEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMatch(match: MatchEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAllMatches(matches: List<MatchEntity>): List<Long>

    @Update
    abstract suspend fun updateMatch(match: MatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertNotification(notification: NotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAllNotifications(notifications: List<NotificationEntity>): List<Long>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    abstract suspend fun getUserById(userId: Long): UserEntity?

    @Query("SELECT name FROM users WHERE id = :userId LIMIT 1")
    abstract suspend fun getUserNameById(userId: Long): String?

    @Query("SELECT * FROM clothing_items WHERE id = :itemId LIMIT 1")
    abstract suspend fun getClothingById(itemId: Long): ClothingItemEntity?

    @Query("SELECT * FROM interests WHERE id = :interestId LIMIT 1")
    abstract suspend fun getInterestById(interestId: Long): InterestEntity?

    @Query("SELECT * FROM interests WHERE senderUserId = :senderUserId AND targetItemId = :targetItemId LIMIT 1")
    abstract suspend fun getInterestBySenderAndTarget(senderUserId: Long, targetItemId: Long): InterestEntity?

    @Query(
        """
        SELECT * FROM interests
        WHERE senderUserId = :senderUserId
          AND receiverUserId = :receiverUserId
          AND status = 'PENDING'
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    abstract suspend fun getPendingInterestBetweenUsers(senderUserId: Long, receiverUserId: Long): InterestEntity?

    @Query("SELECT * FROM matches WHERE id = :matchId LIMIT 1")
    abstract suspend fun getMatchById(matchId: Long): MatchEntity?

    @Query(
        """
        SELECT COUNT(*) FROM matches
        WHERE status = 'ACTIVE' AND (user1ItemId = :itemId OR user2ItemId = :itemId)
        """
    )
    abstract suspend fun countActiveMatchesUsingItem(itemId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM matches
        WHERE status = 'ACTIVE' AND (user1ItemId IN (:itemA, :itemB) OR user2ItemId IN (:itemA, :itemB))
        """
    )
    abstract suspend fun countActiveMatchesUsingEitherItem(itemA: Long, itemB: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM matches
        WHERE status IN ('ACTIVE', 'COMPLETED')
          AND ((user1ItemId = :itemA AND user2ItemId = :itemB) OR (user1ItemId = :itemB AND user2ItemId = :itemA))
        """
    )
    abstract suspend fun countActiveOrCompletedMatchForPair(itemA: Long, itemB: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM clothing_items c
        WHERE c.ownerId = :userId
          AND c.status = 'AVAILABLE'
          AND c.isBarterAllowed = 1
          AND c.id NOT IN (
            SELECT user1ItemId FROM matches WHERE status = 'ACTIVE'
            UNION
            SELECT user2ItemId FROM matches WHERE status = 'ACTIVE'
          )
        """
    )
    abstract suspend fun countEligibleBarterItemsForUser(userId: Long): Int

    @Query("UPDATE interests SET status = :status, respondedAt = :respondedAt WHERE id = :interestId")
    abstract suspend fun markInterestStatus(interestId: Long, status: InterestStatus, respondedAt: Long)

    @Query(
        """
        UPDATE interests
        SET status = :newStatus, respondedAt = :respondedAt
        WHERE senderUserId = :senderUserId
          AND receiverUserId = :receiverUserId
          AND targetItemId = :targetItemId
          AND status = 'PENDING'
        """
    )
    abstract suspend fun resolveCrossPendingInterestForSamePair(
        senderUserId: Long,
        receiverUserId: Long,
        targetItemId: Long,
        newStatus: InterestStatus,
        respondedAt: Long
    )

    @Query("UPDATE matches SET status = :status, updatedAt = :updatedAt WHERE id = :matchId")
    abstract suspend fun updateMatchStatus(matchId: Long, status: MatchStatus, updatedAt: Long)

    @Query("UPDATE matches SET status = 'COMPLETED', meetingLocation = :location, updatedAt = :updatedAt WHERE id = :matchId")
    abstract suspend fun updateMatchCompleted(matchId: Long, location: String?, updatedAt: Long)

    @Query("UPDATE clothing_items SET status = :status, updatedAt = :updatedAt WHERE id IN (:itemIds)")
    abstract suspend fun updateItemsStatus(itemIds: List<Long>, status: ItemStatus, updatedAt: Long)

    @Query(
        """
        UPDATE interests
        SET status = 'INVALIDATED', respondedAt = :respondedAt
        WHERE targetItemId IN (:itemIds) AND status = 'PENDING'
        """
    )
    abstract suspend fun invalidatePendingInterestsForItems(itemIds: List<Long>, respondedAt: Long)

    @Query(
        """
        SELECT
            m.id AS matchId,
            m.sourceInterestId AS sourceInterestId,
            m.user1Id AS user1Id,
            m.user1ItemId AS user1ItemId,
            m.user2Id AS user2Id,
            m.user2ItemId AS user2ItemId,
            m.status AS status,
            m.meetingLocation AS meetingLocation,
            m.createdAt AS createdAt,
            m.updatedAt AS updatedAt,
            u1.name AS user1Name,
            u1.city AS user1City,
            u2.name AS user2Name,
            u2.city AS user2City
        FROM matches m
        JOIN users u1 ON m.user1Id = u1.id
        JOIN users u2 ON m.user2Id = u2.id
        WHERE m.id = :matchId
        LIMIT 1
        """
    )
    abstract suspend fun getMatchWithDetails(matchId: Long): MatchWithDetailsRow?

    @Query(
        """
        SELECT
            m.id AS matchId,
            m.sourceInterestId AS sourceInterestId,
            m.user1Id AS user1Id,
            m.user1ItemId AS user1ItemId,
            m.user2Id AS user2Id,
            m.user2ItemId AS user2ItemId,
            m.status AS status,
            m.meetingLocation AS meetingLocation,
            m.createdAt AS createdAt,
            m.updatedAt AS updatedAt,
            u1.name AS user1Name,
            u1.city AS user1City,
            u2.name AS user2Name,
            u2.city AS user2City
        FROM matches m
        JOIN users u1 ON m.user1Id = u1.id
        JOIN users u2 ON m.user2Id = u2.id
        WHERE m.id = :matchId
        LIMIT 1
        """
    )
    abstract fun getMatchWithDetailsFlow(matchId: Long): Flow<MatchWithDetailsRow?>

    @Transaction
    open suspend fun sendInterest(
        senderUserId: Long,
        targetItemId: Long,
        nowMillis: Long
    ): SendInterestDaoResult {

        val senderUser = getUserById(senderUserId) ?: return SendInterestDaoResult.SenderNotFound

        val existingInterest = getInterestBySenderAndTarget(senderUserId, targetItemId)
        if (existingInterest != null) {
            return SendInterestDaoResult.AlreadyInterested(existingInterest.status)
        }

        val eligibleCount = countEligibleBarterItemsForUser(senderUserId)
        if (eligibleCount == 0) {
            return SendInterestDaoResult.RequiresBarterableItemInWardrobe
        }

        val targetItem = getClothingById(targetItemId) ?: return SendInterestDaoResult.TargetItemNotFound
        if (targetItem.ownerId == senderUserId) {
            return SendInterestDaoResult.CannotInterestOwnItem
        }
        if (targetItem.status != ItemStatus.AVAILABLE || !targetItem.isBarterAllowed || countActiveMatchesUsingItem(targetItemId) > 0) {
            return SendInterestDaoResult.TargetItemNotEligible
        }

        val reverseInterest = getPendingInterestBetweenUsers(targetItem.ownerId, senderUserId)
        if (reverseInterest != null) {
            val reverseItem = getClothingById(reverseInterest.targetItemId)
            if (reverseItem != null &&
                reverseItem.ownerId == senderUserId &&
                reverseItem.status == ItemStatus.AVAILABLE &&
                reverseItem.isBarterAllowed &&
                countActiveMatchesUsingItem(reverseItem.id) == 0 &&
                countActiveOrCompletedMatchForPair(targetItem.id, reverseItem.id) == 0
            ) {
                val targetUserName = getUserNameById(targetItem.ownerId) ?: "Pengguna"

                val interestId = insertInterest(
                    InterestEntity(
                        senderUserId = senderUserId,
                        receiverUserId = targetItem.ownerId,
                        targetItemId = targetItemId,
                        status = InterestStatus.RESOLVED_BY_MATCH,
                        createdAt = nowMillis,
                        respondedAt = nowMillis
                    )
                )

                markInterestStatus(reverseInterest.id, InterestStatus.ACCEPTED_MATCHED, nowMillis)

                val matchId = insertMatch(
                    MatchEntity(
                        sourceInterestId = reverseInterest.id,
                        user1Id = targetItem.ownerId,
                        user1ItemId = targetItem.id,
                        user2Id = senderUserId,
                        user2ItemId = reverseItem.id,
                        status = MatchStatus.ACTIVE,
                        meetingLocation = null,
                        createdAt = nowMillis,
                        updatedAt = nowMillis
                    )
                )

                insertNotification(
                    NotificationEntity(
                        userId = targetItem.ownerId,
                        type = NotificationType.MATCH_VERIFIED,
                        title = "Match terverifikasi dengan ${senderUser.name}",
                        body = "${targetItem.name} ↔ ${reverseItem.name} siap didiskusikan di ruang Chat.",
                        relatedInterestId = reverseInterest.id,
                        relatedMatchId = matchId,
                        isRead = false,
                        createdAt = nowMillis
                    )
                )

                insertNotification(
                    NotificationEntity(
                        userId = senderUserId,
                        type = NotificationType.MATCH_VERIFIED,
                        title = "Match terverifikasi dengan $targetUserName",
                        body = "${reverseItem.name} ↔ ${targetItem.name} siap didiskusikan di ruang Chat.",
                        relatedInterestId = interestId,
                        relatedMatchId = matchId,
                        isRead = false,
                        createdAt = nowMillis
                    )
                )

                return SendInterestDaoResult.Success(interestId, matchId)
            }
        }

        val interestId = insertInterest(
            InterestEntity(
                senderUserId = senderUserId,
                receiverUserId = targetItem.ownerId,
                targetItemId = targetItemId,
                status = InterestStatus.PENDING,
                createdAt = nowMillis,
                respondedAt = null
            )
        )

        insertNotification(
            NotificationEntity(
                userId = targetItem.ownerId,
                type = NotificationType.INCOMING_INTEREST,
                title = "${senderUser.name} tertarik pada ${targetItem.name}",
                body = "Buka lemari ${senderUser.name} dan pilih tepat satu pakaian berizin barter.",
                relatedInterestId = interestId,
                relatedMatchId = null,
                isRead = false,
                createdAt = nowMillis
            )
        )

        return SendInterestDaoResult.Success(interestId)
    }

    @Transaction
    open suspend fun respondToInterestAndCreateMatch(
        actorUserId: Long,
        interestId: Long,
        chosenSenderItemId: Long,
        nowMillis: Long
    ): RespondInterestDaoResult {

        val interest = getInterestById(interestId)
            ?: return RespondInterestDaoResult.InterestAlreadyProcessedOrInvalid
        if (interest.receiverUserId != actorUserId || interest.status != InterestStatus.PENDING) {
            return RespondInterestDaoResult.InterestAlreadyProcessedOrInvalid
        }

        val targetItem = getClothingById(interest.targetItemId)
        if (targetItem == null) {
            markInterestStatus(interest.id, InterestStatus.INVALIDATED, nowMillis)
            return RespondInterestDaoResult.TargetItemNotFound(true)
        }
        if (targetItem.ownerId != actorUserId || actorUserId == interest.senderUserId) {
            return RespondInterestDaoResult.InvalidItemOwnership
        }
        if (targetItem.status != ItemStatus.AVAILABLE) {
            markInterestStatus(interest.id, InterestStatus.INVALIDATED, nowMillis)
            return RespondInterestDaoResult.TargetItemUnavailable(true)
        }

        val chosenSenderItem = getClothingById(chosenSenderItemId)
            ?: return RespondInterestDaoResult.ChosenItemNotFound
        if (chosenSenderItem.ownerId != interest.senderUserId) {
            return RespondInterestDaoResult.InvalidItemOwnership
        }
        if (chosenSenderItem.status != ItemStatus.AVAILABLE) {
            return RespondInterestDaoResult.ChosenItemUnavailable
        }

        if (!targetItem.isBarterAllowed || !chosenSenderItem.isBarterAllowed) {
            return RespondInterestDaoResult.BarterNotAllowed
        }

        if (countActiveMatchesUsingEitherItem(targetItem.id, chosenSenderItem.id) > 0) {
            return RespondInterestDaoResult.ItemAlreadyInActiveMatch
        }
        if (countActiveOrCompletedMatchForPair(targetItem.id, chosenSenderItem.id) > 0) {
            return RespondInterestDaoResult.PairAlreadyMatched
        }

        val actorName = getUserNameById(actorUserId) ?: ""
        val senderName = getUserNameById(interest.senderUserId) ?: ""

        val matchId = insertMatch(
            MatchEntity(
                sourceInterestId = interest.id,
                user1Id = actorUserId,
                user1ItemId = targetItem.id,
                user2Id = interest.senderUserId,
                user2ItemId = chosenSenderItem.id,
                status = MatchStatus.ACTIVE,
                meetingLocation = null,
                createdAt = nowMillis,
                updatedAt = nowMillis
            )
        )

        markInterestStatus(interest.id, InterestStatus.ACCEPTED_MATCHED, nowMillis)

        resolveCrossPendingInterestForSamePair(
            senderUserId = actorUserId,
            receiverUserId = interest.senderUserId,
            targetItemId = chosenSenderItem.id,
            newStatus = InterestStatus.RESOLVED_BY_MATCH,
            respondedAt = nowMillis
        )

        insertNotification(
            NotificationEntity(
                userId = interest.senderUserId,
                type = NotificationType.MATCH_VERIFIED,
                title = "Match terverifikasi dengan $actorName",
                body = "${targetItem.name} ↔ ${chosenSenderItem.name} siap didiskusikan di ruang Chat.",
                relatedInterestId = interest.id,
                relatedMatchId = matchId,
                isRead = false,
                createdAt = nowMillis
            )
        )

        insertNotification(
            NotificationEntity(
                userId = actorUserId,
                type = NotificationType.MATCH_VERIFIED,
                title = "Match terverifikasi dengan $senderName",
                body = "${targetItem.name} ↔ ${chosenSenderItem.name} siap didiskusikan di ruang Chat.",
                relatedInterestId = interest.id,
                relatedMatchId = matchId,
                isRead = false,
                createdAt = nowMillis
            )
        )

        return RespondInterestDaoResult.Success(matchId)
    }

    @Transaction
    open suspend fun cancelMatch(
        matchId: Long,
        actorUserId: Long,
        nowMillis: Long
    ): CancelMatchDaoResult {
        val match = getMatchById(matchId) ?: return CancelMatchDaoResult.MatchNotFound
        if (actorUserId != match.user1Id && actorUserId != match.user2Id) {
            return CancelMatchDaoResult.NotMatchParticipant
        }
        if (match.status != MatchStatus.ACTIVE) {
            return CancelMatchDaoResult.MatchNotActive(match.status)
        }

        updateMatchStatus(matchId, MatchStatus.CANCELLED, nowMillis)

        val counterpartUserId = if (actorUserId == match.user1Id) match.user2Id else match.user1Id
        insertNotification(
            NotificationEntity(
                userId = counterpartUserId,
                type = NotificationType.MATCH_CANCELLED,
                title = "Match dibatalkan",
                body = "Rekan barter membatalkan Match.",
                relatedInterestId = match.sourceInterestId,
                relatedMatchId = matchId,
                isRead = false,
                createdAt = nowMillis
            )
        )

        return CancelMatchDaoResult.Success
    }

    @Transaction
    open suspend fun completeMatchExchange(
        matchId: Long,
        actorUserId: Long,
        meetingLocation: String?,
        nowMillis: Long
    ): CompleteMatchDaoResult {
        val match = getMatchById(matchId) ?: return CompleteMatchDaoResult.MatchNotFound
        if (actorUserId != match.user1Id && actorUserId != match.user2Id) {
            return CompleteMatchDaoResult.NotMatchParticipant
        }
        if (match.status != MatchStatus.ACTIVE) {
            return CompleteMatchDaoResult.MatchNotActive(match.status)
        }

        val item1 = getClothingById(match.user1ItemId)
        val item2 = getClothingById(match.user2ItemId)
        if (item1 == null || item2 == null || item1.status != ItemStatus.AVAILABLE || item2.status != ItemStatus.AVAILABLE) {
            return CompleteMatchDaoResult.ItemsNoLongerAvailable
        }

        updateMatchCompleted(matchId, meetingLocation?.trim(), nowMillis)
        updateItemsStatus(listOf(match.user1ItemId, match.user2ItemId), ItemStatus.EXCHANGED, nowMillis)
        invalidatePendingInterestsForItems(listOf(match.user1ItemId, match.user2ItemId), nowMillis)

        val counterpartUserId = if (actorUserId == match.user1Id) match.user2Id else match.user1Id
        insertNotification(
            NotificationEntity(
                userId = counterpartUserId,
                type = NotificationType.EXCHANGE_COMPLETED,
                title = "Pertukaran selesai",
                body = "Pasangan pakaian telah ditandai sudah ditukar. Berikan rating & ulasan.",
                relatedInterestId = match.sourceInterestId,
                relatedMatchId = matchId,
                isRead = false,
                createdAt = nowMillis
            )
        )

        return CompleteMatchDaoResult.Success
    }

    @Query(
        """
        SELECT
            i.id AS interestId,
            i.targetItemId AS targetItemId,
            c.name AS targetItemName,
            c.category AS targetItemCategory,
            c.mainPhotoUri AS targetItemPhotoUri,
            c.status AS targetItemStatus,
            u.id AS ownerId,
            u.name AS ownerName,
            u.city AS ownerCity,
            i.status AS interestStatus,
            i.createdAt AS createdAt,
            m.id AS matchId,
            m.status AS matchStatus
        FROM interests i
        JOIN clothing_items c ON i.targetItemId = c.id
        JOIN users u ON c.ownerId = u.id
        LEFT JOIN matches m ON m.sourceInterestId = i.id
        WHERE i.senderUserId = :userId
        ORDER BY i.createdAt DESC
        """
    )
    abstract fun getOutgoingInterestsFlow(userId: Long): Flow<List<OutgoingInterestRow>>

    @Query("DELETE FROM swipe_passes WHERE userId = :userId AND targetItemId = :targetItemId")
    abstract suspend fun deleteSpecificSwipePass(userId: Long, targetItemId: Long): Int

    @Transaction
    open suspend fun convertSwipePassToInterest(
        senderUserId: Long,
        targetItemId: Long,
        nowMillis: Long
    ): SendInterestDaoResult {
        val result = sendInterest(senderUserId, targetItemId, nowMillis)
        if (result is SendInterestDaoResult.Success) {
            deleteSpecificSwipePass(senderUserId, targetItemId)
        }
        return result
    }
}
