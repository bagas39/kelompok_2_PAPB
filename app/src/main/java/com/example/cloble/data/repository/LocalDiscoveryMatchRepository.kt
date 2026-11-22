package com.example.cloble.data.repository

import com.example.cloble.data.local.dao.CancelInterestDaoResult
import com.example.cloble.data.local.dao.CancelMatchDaoResult
import com.example.cloble.data.local.dao.ClothingItemDao
import com.example.cloble.data.local.dao.CompleteMatchDaoResult
import com.example.cloble.data.local.dao.DiscoveryDao
import com.example.cloble.data.local.dao.InterestMatchDao
import com.example.cloble.data.local.dao.MatchWithDetailsRow
import com.example.cloble.data.local.dao.NotificationDao
import com.example.cloble.data.local.dao.RespondInterestDaoResult
import com.example.cloble.data.local.dao.ReviewDao
import com.example.cloble.data.local.dao.SendInterestDaoResult
import com.example.cloble.data.local.dao.UserDao
import com.example.cloble.data.local.entity.SwipePassEntity
import com.example.cloble.domain.model.DiscoverCandidate
import com.example.cloble.domain.model.DiscoverFilter
import com.example.cloble.domain.model.NotificationFeedItem
import com.example.cloble.domain.model.OutgoingInterestSummary
import com.example.cloble.domain.model.PassedClothingSummary
import com.example.cloble.domain.model.VerifiedMatchDetail
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.result.CancelInterestResult
import com.example.cloble.domain.result.CancelMatchResult
import com.example.cloble.domain.result.CompleteMatchResult
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.result.DeclineInterestResult
import com.example.cloble.domain.result.RespondInterestResult
import com.example.cloble.domain.result.SendInterestResult
import com.example.cloble.domain.util.DistanceCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class LocalDiscoveryMatchRepository(
    private val discoveryDao: DiscoveryDao,
    private val interestMatchDao: InterestMatchDao,
    private val notificationDao: NotificationDao,
    private val clothingDao: ClothingItemDao,
    private val userDao: UserDao,
    private val reviewDao: ReviewDao
) : DiscoveryMatchRepository {

    override fun observeDiscoverCandidates(
        viewerUserId: Long,
        filter: DiscoverFilter
    ): Flow<List<DiscoverCandidate>> {
        return combine(
            userDao.getUserByIdFlow(viewerUserId),
            discoveryDao.getDiscoverCandidatesFlow(viewerUserId)
        ) { viewer, rawList ->
            if (viewer == null) return@combine emptyList()

            val maxRadius = filter.maxRadiusKm ?: viewer.preferredRadiusKm
            val selectedGenders = filter.selectedGenders
            val selectedCategories = filter.selectedCategories
            val selectedSizes = filter.selectedSizes
            val selectedColors = filter.selectedColors

            val (viewerLat, viewerLon) = DistanceCalculator.resolveEffectiveCoordinates(
                viewer.city,
                viewer.latitude,
                viewer.longitude
            )

            rawList.mapNotNull { raw ->
                val (ownerLat, ownerLon) = DistanceCalculator.resolveEffectiveCoordinates(
                    raw.ownerCity,
                    raw.ownerLatitude,
                    raw.ownerLongitude
                )
                val distanceKm = DistanceCalculator.calculateKm(
                    lat1 = viewerLat,
                    lon1 = viewerLon,
                    lat2 = ownerLat,
                    lon2 = ownerLon
                )

                if (distanceKm > maxRadius) return@mapNotNull null

                if (selectedGenders.isNotEmpty()) {
                    val matchesGender = selectedGenders.contains(raw.item.gender) ||
                            selectedGenders.contains("Uniseks") ||
                            raw.item.gender.equals("Uniseks", ignoreCase = true)
                    if (!matchesGender) return@mapNotNull null
                } else if (!viewer.preferredGender.equals("Uniseks", ignoreCase = true) &&
                    !raw.item.gender.equals("Uniseks", ignoreCase = true) &&
                    !raw.item.gender.equals(viewer.preferredGender, ignoreCase = true)
                ) {
                    return@mapNotNull null
                }

                if (selectedCategories.isNotEmpty() && !selectedCategories.contains(raw.item.category)) {
                    return@mapNotNull null
                }

                if (selectedSizes.isNotEmpty() && !selectedSizes.contains(raw.item.size)) {
                    return@mapNotNull null
                }

                if (selectedColors.isNotEmpty() && !selectedColors.contains(raw.item.color)) {
                    return@mapNotNull null
                }

                EntityMappers.toDomainDiscoverCandidate(raw, distanceKm)
            }
        }
    }

    override fun observeMatchDetail(
        matchId: Long,
        viewerUserId: Long
    ): Flow<VerifiedMatchDetail?> {
        return interestMatchDao.getMatchWithDetailsFlow(matchId).map { row ->
            row?.takeIf { it.user1Id == viewerUserId || it.user2Id == viewerUserId }
                ?.let { mapToVerifiedMatchDetail(it, viewerUserId) }
        }
    }

    override fun observeNotifications(userId: Long): Flow<List<NotificationFeedItem>> {
        return notificationDao.getNotificationsFlow(userId).map { rows ->
            rows.map { EntityMappers.toDomainNotificationItem(it) }
        }
    }

    override fun observeUnreadNotificationCount(userId: Long): Flow<Int> {
        return notificationDao.getUnreadNotificationCount(userId)
    }

    override fun observeOutgoingInterests(userId: Long): Flow<List<OutgoingInterestSummary>> {
        return interestMatchDao.getOutgoingInterestsFlow(userId).map { rows ->
            rows.map { EntityMappers.toDomainOutgoingInterest(it) }
        }
    }

    override fun observePassedClothing(userId: Long): Flow<List<PassedClothingSummary>> {
        return discoveryDao.getPassedClothingFlow(userId).map { rows ->
            rows.map { EntityMappers.toDomainPassedClothing(it) }
        }
    }

    override suspend fun passCandidate(userId: Long, targetItemId: Long): Boolean {
        return try {
            discoveryDao.insertSwipePass(
                SwipePassEntity(
                    userId = userId,
                    targetItemId = targetItemId,
                    createdAt = System.currentTimeMillis()
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun restorePassToDiscover(userId: Long, targetItemId: Long): Boolean {
        val deleted = discoveryDao.deleteSwipePass(userId, targetItemId)
        return deleted > 0
    }

    override suspend fun convertPassToInterest(
        userId: Long,
        targetItemId: Long
    ): SendInterestResult {
        val now = System.currentTimeMillis()
        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { SendInterestResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { SendInterestResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { SendInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            when (val daoResult = interestMatchDao.convertSwipePassToInterest(userId, targetItemId, now)) {
                is SendInterestDaoResult.Success -> SendInterestResult.Success(daoResult.interestId, daoResult.matchId)
                is SendInterestDaoResult.SenderNotFound -> SendInterestResult.InvalidOrMissingSenderSession
                is SendInterestDaoResult.AlreadyInterested -> SendInterestResult.AlreadyInterested(daoResult.existingStatus)
                is SendInterestDaoResult.RequiresBarterableItemInWardrobe -> SendInterestResult.RequiresBarterableItemInWardrobe
                is SendInterestDaoResult.TargetItemNotFound -> SendInterestResult.TargetItemNotFound
                is SendInterestDaoResult.CannotInterestOwnItem -> SendInterestResult.CannotInterestOwnItem
                is SendInterestDaoResult.TargetItemNotEligible -> SendInterestResult.TargetItemNotEligible
            }
        }
    }

    override suspend fun sendInterest(
        senderId: Long,
        targetItemId: Long
    ): SendInterestResult {
        val now = System.currentTimeMillis()
        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { SendInterestResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { SendInterestResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { SendInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            when (val daoResult = interestMatchDao.sendInterest(senderId, targetItemId, now)) {
                is SendInterestDaoResult.Success -> SendInterestResult.Success(daoResult.interestId, daoResult.matchId)
                is SendInterestDaoResult.SenderNotFound -> SendInterestResult.InvalidOrMissingSenderSession
                is SendInterestDaoResult.AlreadyInterested -> SendInterestResult.AlreadyInterested(daoResult.existingStatus)
                is SendInterestDaoResult.RequiresBarterableItemInWardrobe -> SendInterestResult.RequiresBarterableItemInWardrobe
                is SendInterestDaoResult.TargetItemNotFound -> SendInterestResult.TargetItemNotFound
                is SendInterestDaoResult.CannotInterestOwnItem -> SendInterestResult.CannotInterestOwnItem
                is SendInterestDaoResult.TargetItemNotEligible -> SendInterestResult.TargetItemNotEligible
            }
        }
    }

    override suspend fun cancelOutgoingInterest(
        userId: Long,
        interestId: Long
    ): CancelInterestResult {
        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { CancelInterestResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { CancelInterestResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { CancelInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            when (val daoResult = interestMatchDao.cancelOutgoingInterest(interestId, userId)) {
                is CancelInterestDaoResult.Success -> CancelInterestResult.Success
                is CancelInterestDaoResult.InterestNotFound -> CancelInterestResult.InterestNotFound
                is CancelInterestDaoResult.NotInterestOwner -> CancelInterestResult.NotInterestOwner
                is CancelInterestDaoResult.InterestAlreadyProcessed -> CancelInterestResult.InterestAlreadyProcessed(daoResult.currentStatus)
            }
        }
    }

    override suspend fun respondToInterestAndCreateMatch(
        actorUserId: Long,
        interestId: Long,
        chosenSenderItemId: Long
    ): RespondInterestResult {
        val now = System.currentTimeMillis()
        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { RespondInterestResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { RespondInterestResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { RespondInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            when (
                val daoResult = interestMatchDao.respondToInterestAndCreateMatch(
                    actorUserId = actorUserId,
                    interestId = interestId,
                    chosenSenderItemId = chosenSenderItemId,
                    nowMillis = now
                )
            ) {
                is RespondInterestDaoResult.Success -> RespondInterestResult.Success(daoResult.matchId)
                is RespondInterestDaoResult.InterestAlreadyProcessedOrInvalid -> RespondInterestResult.InterestAlreadyProcessedOrInvalid
                is RespondInterestDaoResult.TargetItemNotFound -> RespondInterestResult.TargetItemNotFound(daoResult.interestInvalidated)
                is RespondInterestDaoResult.TargetItemUnavailable -> RespondInterestResult.TargetItemUnavailable(daoResult.interestInvalidated)
                is RespondInterestDaoResult.ChosenItemNotFound -> RespondInterestResult.ChosenItemNotFound
                is RespondInterestDaoResult.ChosenItemUnavailable -> RespondInterestResult.ChosenItemUnavailable
                is RespondInterestDaoResult.InvalidItemOwnership -> RespondInterestResult.InvalidItemOwnership
                is RespondInterestDaoResult.BarterNotAllowed -> RespondInterestResult.BarterNotAllowed
                is RespondInterestDaoResult.ItemAlreadyInActiveMatch -> RespondInterestResult.ItemAlreadyInActiveMatch
                is RespondInterestDaoResult.PairAlreadyMatched -> RespondInterestResult.PairAlreadyMatched
            }
        }
    }

    override suspend fun declineIncomingInterest(
        actorUserId: Long,
        interestId: Long
    ): DeclineInterestResult {
        val now = System.currentTimeMillis()
        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { DeclineInterestResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { DeclineInterestResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { DeclineInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            val declined = interestMatchDao.declineIncomingInterest(actorUserId, interestId, now)
            if (declined) DeclineInterestResult.Success else DeclineInterestResult.InterestAlreadyProcessedOrInvalid
        }
    }

    override suspend fun cancelMatch(
        matchId: Long,
        actorUserId: Long
    ): CancelMatchResult {
        val now = System.currentTimeMillis()
        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { CancelMatchResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { CancelMatchResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { CancelMatchResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            when (val daoResult = interestMatchDao.cancelMatch(matchId, actorUserId, now)) {
                is CancelMatchDaoResult.Success -> CancelMatchResult.Success
                is CancelMatchDaoResult.MatchNotFound -> CancelMatchResult.MatchNotFound
                is CancelMatchDaoResult.NotMatchParticipant -> CancelMatchResult.NotMatchParticipant
                is CancelMatchDaoResult.MatchNotActive -> CancelMatchResult.MatchNotActive(daoResult.currentStatus)
            }
        }
    }

    override suspend fun completeMatchExchange(
        matchId: Long,
        actorUserId: Long,
        meetingLocation: String?
    ): CompleteMatchResult {
        val now = System.currentTimeMillis()
        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { CompleteMatchResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { CompleteMatchResult.DatabaseError(DbFailure.DuplicateOrConstraintViolation(it)) },
            onUnexpectedDatabaseError = { CompleteMatchResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            when (val daoResult = interestMatchDao.completeMatchExchange(matchId, actorUserId, meetingLocation, now)) {
                is CompleteMatchDaoResult.Success -> CompleteMatchResult.Success
                is CompleteMatchDaoResult.MatchNotFound -> CompleteMatchResult.MatchNotFound
                is CompleteMatchDaoResult.NotMatchParticipant -> CompleteMatchResult.NotMatchParticipant
                is CompleteMatchDaoResult.MatchNotActive -> CompleteMatchResult.MatchNotActive(daoResult.currentStatus)
                is CompleteMatchDaoResult.ItemsNoLongerAvailable -> CompleteMatchResult.ItemsNoLongerAvailable
            }
        }
    }

    override suspend fun markNotificationsRead(userId: Long) {
        notificationDao.markNotificationsRead(userId)
    }

    override suspend fun markNotificationReadById(notificationId: Long) {
        notificationDao.markNotificationReadById(notificationId)
    }

    private suspend fun mapToVerifiedMatchDetail(
        row: MatchWithDetailsRow,
        viewerUserId: Long
    ): VerifiedMatchDetail {
        val isUser1 = row.user1Id == viewerUserId

        val myUserId = if (isUser1) row.user1Id else row.user2Id
        val partnerUserId = if (isUser1) row.user2Id else row.user1Id
        val partnerName = if (isUser1) row.user2Name else row.user1Name
        val partnerCity = if (isUser1) row.user2City else row.user1City
        val partnerAvatarUri = if (isUser1) row.user2AvatarUri else row.user1AvatarUri

        val partnerAvg = reviewDao.getAverageRatingForUser(partnerUserId) ?: 0.0
        val partnerCount = reviewDao.getReviewCountForUser(partnerUserId)

        val myItemId = if (isUser1) row.user1ItemId else row.user2ItemId
        val partnerItemId = if (isUser1) row.user2ItemId else row.user1ItemId

        val myItemEntity = clothingDao.getClothingById(myItemId)
        val partnerItemEntity = clothingDao.getClothingById(partnerItemId)

        val myItem = myItemEntity?.let { EntityMappers.toDomainClothingItem(it) }
            ?: throw IllegalStateException("My item $myItemId not found for match ${row.matchId}")
        val partnerItem = partnerItemEntity?.let { EntityMappers.toDomainClothingItem(it) }
            ?: throw IllegalStateException("Partner item $partnerItemId not found for match ${row.matchId}")

        return VerifiedMatchDetail(
            matchId = row.matchId,
            status = row.status,
            meetingLocation = row.meetingLocation,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            myUserId = myUserId,
            partnerUserId = partnerUserId,
            partnerName = partnerName,
            partnerCity = partnerCity,
            partnerAvatarUri = partnerAvatarUri,
            partnerAverageRating = partnerAvg,
            partnerReviewCount = partnerCount,
            myItem = myItem,
            partnerItem = partnerItem
        )
    }
}
