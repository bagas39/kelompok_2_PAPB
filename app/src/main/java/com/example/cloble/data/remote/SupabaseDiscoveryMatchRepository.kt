package com.example.cloble.data.remote

import com.example.cloble.data.local.dao.OfflineChatCacheDao
import com.example.cloble.data.repository.OfflineChatCacheMappers
import com.example.cloble.domain.model.DiscoverCandidate
import com.example.cloble.domain.model.DiscoverFilter
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class SupabaseDiscoveryMatchRepository(
    private val client: SupabaseClient,
    private val invalidator: SupabaseDataInvalidator,
    private val realtimeClient: SupabaseRealtimeClient? = null,
    private val cacheDao: OfflineChatCacheDao? = null
) : DiscoveryMatchRepository {

    private sealed interface RemoteMatchDetailResult {
        data class Found(val detail: VerifiedMatchDetail) : RemoteMatchDetailResult
        data object NotFoundOrForbidden : RemoteMatchDetailResult
        data object NetworkError : RemoteMatchDetailResult
    }

    private fun parseLongIdSet(arr: JSONArray?): Set<Long> {
        if (arr == null) return emptySet()
        val set = mutableSetOf<Long>()
        for (i in 0 until arr.length()) {
            val id = arr.optLong(i)
            if (id > 0L) set.add(id)
        }
        return set
    }

    override fun observeDiscoverCandidates(
        viewerUserId: Long,
        filter: DiscoverFilter
    ): Flow<List<DiscoverCandidate>> =
        combine(
            kotlinx.coroutines.flow.flow {
                emit(Unit)
                invalidator.discoveryTrigger.collect { emit(Unit) }
            },
            kotlinx.coroutines.flow.flow {
                emit(Unit)
                invalidator.userTrigger.collect { emit(Unit) }
            }
        ) { _, _ ->
            fetchDiscoverCandidates(viewerUserId, filter)
        }.flowOn(Dispatchers.IO)

    private suspend fun fetchDiscoverCandidates(
        viewerUserId: Long,
        filter: DiscoverFilter
    ): List<DiscoverCandidate> {
        return try {
            val params = JSONObject().apply {
                put("p_sizes", if (filter.selectedSizes.isNotEmpty()) JSONArray(filter.selectedSizes.toList()) else JSONObject.NULL)
                put("p_genders", if (filter.selectedGenders.isNotEmpty()) JSONArray(filter.selectedGenders.toList()) else JSONObject.NULL)
                put("p_categories", if (filter.selectedCategories.isNotEmpty()) JSONArray(filter.selectedCategories.toList()) else JSONObject.NULL)
                put("p_colors", if (filter.selectedColors.isNotEmpty()) JSONArray(filter.selectedColors.toList()) else JSONObject.NULL)
                put("p_max_radius_km", filter.maxRadiusKm ?: JSONObject.NULL)
            }
            val resp = client.rpc("rpc_get_discover_candidates", params)
            if (resp.optString("code") != "SUCCESS") return emptyList()
            val arr = resp.optJSONArray("candidates") ?: return emptyList()
            val candidates = mutableListOf<DiscoverCandidate>()
            for (i in 0 until arr.length()) {
                val raw = arr.getJSONObject(i)
                val distanceKm = raw.optInt("distanceKm", 0)
                val avg = raw.optDouble("ownerAverageRating", 0.0)
                val count = raw.optInt("ownerReviewCount", 0)
                candidates.add(SupabaseMappers.parseDiscoverCandidate(raw, distanceKm, avg, count))
            }
            candidates
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun observeMatchDetail(
        matchId: Long,
        viewerUserId: Long
    ): Flow<VerifiedMatchDetail?> = channelFlow {
        val matchTopic = "realtime:match_$matchId"
        val userTopic = "realtime:user_threads_$viewerUserId"
        realtimeClient?.subscribeTopic(matchTopic)
        realtimeClient?.subscribeTopic(userTopic)

        val refreshTrigger = Channel<Unit>(Channel.CONFLATED)
        var cacheJob: Job? = null
        val syncJob: Job

        if (cacheDao != null) {
            val initialSyncDone = AtomicBoolean(false)
            val hasEmittedCache = AtomicBoolean(false)

            cacheJob = launch {
                cacheDao.observeMatchDetail(viewerUserId, matchId).collect { entity ->
                    if (entity != null) {
                        hasEmittedCache.set(true)
                        send(OfflineChatCacheMappers.toDomain(entity))
                    } else if (initialSyncDone.get()) {
                        send(null)
                    }
                }
            }

            syncJob = launch {
                for (unit in refreshTrigger) {
                    when (val result = fetchMatchDetailResult(matchId, viewerUserId)) {
                        is RemoteMatchDetailResult.Found -> {
                            initialSyncDone.set(true)
                            cacheDao.upsertMatchDetail(OfflineChatCacheMappers.toEntity(result.detail))
                        }
                        is RemoteMatchDetailResult.NotFoundOrForbidden -> {
                            initialSyncDone.set(true)
                            cacheDao.deleteMatchDetail(viewerUserId, matchId)
                            if (!hasEmittedCache.get()) {
                                send(null)
                            }
                        }
                        is RemoteMatchDetailResult.NetworkError -> {
                            initialSyncDone.set(true)
                            if (!hasEmittedCache.get()) {
                                send(null)
                            }
                        }
                    }
                }
            }
        } else {
            var lastKnownDetail: VerifiedMatchDetail? = null
            var hasEmitted = false

            syncJob = launch {
                for (unit in refreshTrigger) {
                    when (val result = fetchMatchDetailResult(matchId, viewerUserId)) {
                        is RemoteMatchDetailResult.Found -> {
                            lastKnownDetail = result.detail
                            hasEmitted = true
                            send(result.detail)
                        }
                        is RemoteMatchDetailResult.NotFoundOrForbidden -> {
                            lastKnownDetail = null
                            hasEmitted = true
                            send(null)
                        }
                        is RemoteMatchDetailResult.NetworkError -> {
                            if (!hasEmitted) {
                                hasEmitted = true
                                send(lastKnownDetail)
                            }
                        }
                    }
                }
            }
        }

        refreshTrigger.trySend(Unit)

        val realtimeJob = launch {
            realtimeClient?.incomingMessages?.collect { (incomingTopic, _) ->
                if (incomingTopic == matchTopic || incomingTopic == userTopic) {
                    refreshTrigger.trySend(Unit)
                }
            }
        }
        val discoveryJob = launch {
            invalidator.discoveryTrigger.collect { refreshTrigger.trySend(Unit) }
        }
        val chatJob = launch {
            invalidator.chatTrigger.collect { refreshTrigger.trySend(Unit) }
        }

        awaitClose {
            refreshTrigger.close()
            cacheJob?.cancel()
            syncJob.cancel()
            realtimeJob.cancel()
            discoveryJob.cancel()
            chatJob.cancel()
            realtimeClient?.unsubscribeTopic(matchTopic)
            realtimeClient?.unsubscribeTopic(userTopic)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchMatchDetailResult(
        matchId: Long,
        viewerUserId: Long
    ): RemoteMatchDetailResult {
        return try {
            val resp = client.rpc(
                "rpc_get_match_detail",
                JSONObject().put("p_match_id", matchId)
            )
            when (resp.optString("code")) {
                "SUCCESS" -> {
                    val m = resp.optJSONObject("match") ?: return RemoteMatchDetailResult.NotFoundOrForbidden
                    val partnerObj = resp.optJSONObject("partner") ?: return RemoteMatchDetailResult.NotFoundOrForbidden
                    val myItemObj = resp.optJSONObject("myItem") ?: return RemoteMatchDetailResult.NotFoundOrForbidden
                    val partnerItemObj = resp.optJSONObject("partnerItem") ?: return RemoteMatchDetailResult.NotFoundOrForbidden
                    val myItem = SupabaseMappers.parseClothingItem(myItemObj)
                    val partnerItem = SupabaseMappers.parseClothingItem(partnerItemObj)
                    val matchStatus = try {
                        MatchStatus.valueOf(m.optString("status", "ACTIVE"))
                    } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
                        MatchStatus.ACTIVE
                    }
                    val partnerAvatarUri = partnerObj.optString("avatarUri")
                        .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

                    RemoteMatchDetailResult.Found(
                        VerifiedMatchDetail(
                            matchId = matchId,
                            status = matchStatus,
                            meetingLocation = m.optString("meetingLocation").takeIf {
                                it.isNotBlank() && !it.equals("null", ignoreCase = true)
                            },
                            createdAt = m.optLong("createdAt"),
                            updatedAt = m.optLong("updatedAt"),
                            myUserId = resp.optLong("myUserId", viewerUserId),
                            partnerUserId = partnerObj.optLong("id"),
                            partnerName = partnerObj.optString("name", "Pengguna").ifBlank { "Pengguna" },
                            partnerCity = partnerObj.optString("city", ""),
                            partnerAvatarUri = partnerAvatarUri,
                            partnerAverageRating = partnerObj.optDouble("averageRating", 0.0),
                            partnerReviewCount = partnerObj.optInt("reviewCount", 0),
                            myItem = myItem,
                            partnerItem = partnerItem
                        )
                    )
                }
                else -> RemoteMatchDetailResult.NotFoundOrForbidden
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            RemoteMatchDetailResult.NetworkError
        }
    }

    private fun <T> observeWithUserRealtime(
        userId: Long,
        trigger: Flow<Unit>,
        fetcher: suspend () -> T
    ): Flow<T> = channelFlow {
        val userTopic = "realtime:user_threads_$userId"
        realtimeClient?.subscribeTopic(userTopic)
        val refreshTrigger = Channel<Unit>(Channel.CONFLATED)

        val syncJob = launch {
            for (unit in refreshTrigger) {
                send(fetcher())
            }
        }

        refreshTrigger.trySend(Unit)

        val triggerJob = launch {
            trigger.collect { refreshTrigger.trySend(Unit) }
        }
        val realtimeJob = launch {
            realtimeClient?.incomingMessages?.collect { (incomingTopic, payload) ->
                if (shouldRefreshDiscoveryForRealtime(userTopic, incomingTopic, payload)) {
                    refreshTrigger.trySend(Unit)
                }
            }
        }

        awaitClose {
            refreshTrigger.close()
            syncJob.cancel()
            triggerJob.cancel()
            realtimeJob.cancel()
            realtimeClient?.unsubscribeTopic(userTopic)
        }
    }.flowOn(Dispatchers.IO)

    override fun observeNotifications(userId: Long): Flow<List<NotificationFeedItem>> =
        observeWithUserRealtime(
            userId = userId,
            trigger = invalidator.notificationsTrigger,
            fetcher = { fetchNotifications(userId) }
        )

    private suspend fun fetchNotifications(userId: Long): List<NotificationFeedItem> {
        return try {
            val resp = client.rpc("rpc_get_notifications", JSONObject())
            if (resp.optString("code") != "SUCCESS") return emptyList()
            val activeItemIds = parseLongIdSet(resp.optJSONArray("activeItemIds"))
            val reviewedMatchIds = parseLongIdSet(resp.optJSONArray("reviewedMatchIds"))
            val arr = resp.optJSONArray("notifications") ?: return emptyList()
            val list = mutableListOf<NotificationFeedItem>()
            for (i in 0 until arr.length()) {
                list.add(
                    SupabaseMappers.parseNotification(
                        json = arr.getJSONObject(i),
                        activeItemIds = activeItemIds,
                        reviewedMatchIds = reviewedMatchIds
                    )
                )
            }
            list
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun observeUnreadNotificationCount(userId: Long): Flow<Int> =
        observeWithUserRealtime(
            userId = userId,
            trigger = invalidator.notificationsTrigger,
            fetcher = { fetchUnreadNotificationCount(userId) }
        )

    private suspend fun fetchUnreadNotificationCount(userId: Long): Int {
        return try {
            val resp = client.rpc("rpc_get_unread_notification_count", JSONObject())
            if (resp.optString("code") == "SUCCESS") resp.optInt("count", 0) else 0
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            0
        }
    }

    override fun observeOutgoingInterests(userId: Long): Flow<List<OutgoingInterestSummary>> =
        observeWithUserRealtime(
            userId = userId,
            trigger = invalidator.discoveryTrigger,
            fetcher = { fetchOutgoingInterests(userId) }
        )

    private suspend fun fetchOutgoingInterests(userId: Long): List<OutgoingInterestSummary> {
        return try {
            val resp = client.rpc("rpc_get_outgoing_interests", JSONObject())
            if (resp.optString("code") != "SUCCESS") return emptyList()
            val arr = resp.optJSONArray("interests") ?: return emptyList()
            val activeItemIds = parseLongIdSet(
                resp.optJSONArray("otherActiveItemIds") ?: resp.optJSONArray("activeItemIds")
            )

            List(arr.length()) { index ->
                SupabaseMappers.parseOutgoingInterest(
                    json = arr.getJSONObject(index),
                    activeItemIds = activeItemIds
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun observePassedClothing(userId: Long): Flow<List<PassedClothingSummary>> =
        observeWithUserRealtime(
            userId = userId,
            trigger = invalidator.discoveryTrigger,
            fetcher = { fetchPassedClothing(userId) }
        )

    private suspend fun fetchPassedClothing(userId: Long): List<PassedClothingSummary> {
        return try {
            val resp = client.rpc("rpc_get_passed_clothing", JSONObject())
            if (resp.optString("code") != "SUCCESS") return emptyList()
            val activeItemIds = parseLongIdSet(resp.optJSONArray("activeItemIds"))
            val arr = resp.optJSONArray("passes") ?: return emptyList()
            val list = mutableListOf<PassedClothingSummary>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val clothingId = obj.optLong("targetItemId")
                list.add(
                    SupabaseMappers.parsePassedClothing(
                        json = obj,
                        isInActiveMatch = activeItemIds.contains(clothingId)
                    )
                )
            }
            list
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun passCandidate(userId: Long, targetItemId: Long): Boolean {
        return try {
            val resp = client.rpc(
                "rpc_pass_candidate",
                JSONObject().put("p_target_item_id", targetItemId)
            )
            if (resp.optString("code") == "SUCCESS") {
                invalidator.invalidateDiscovery()
                true
            } else {
                false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun sendInterest(senderId: Long, targetItemId: Long): SendInterestResult {
        return try {
            val params = JSONObject().apply {
                put("p_sender_user_id", senderId)
                put("p_target_item_id", targetItemId)
                put("p_delete_swipe_pass", true)
            }
            val res = client.rpc("rpc_send_interest", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    val isMutual = res.optBoolean("isMutualMatch", false)
                    val matchId = if (isMutual && !res.isNull("matchId")) res.optLong("matchId") else null
                    val receiverUserId = res.optLong("receiverUserId", 0L)
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    if (matchId != null) {
                        invalidator.invalidateChat()
                        invalidator.invalidateWardrobe()
                        notifyMatchParticipantsViaRealtime(
                            matchId = matchId,
                            user1Id = res.optLong("user1Id", 0L),
                            user2Id = res.optLong("user2Id", 0L)
                        )
                    } else if (receiverUserId > 0L) {
                        notifyUserViaRealtime(receiverUserId, "interest_updated")
                    }
                    SendInterestResult.Success(res.optLong("interestId"), matchId)
                }
                "SENDER_NOT_FOUND" -> SendInterestResult.InvalidOrMissingSenderSession
                "ALREADY_INTERESTED" -> {
                    val status = try {
                        InterestStatus.valueOf(res.optString("existingStatus", "PENDING"))
                    } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
                        InterestStatus.PENDING
                    }
                    SendInterestResult.AlreadyInterested(status)
                }
                "REQUIRES_BARTERABLE_ITEM" -> SendInterestResult.RequiresBarterableItemInWardrobe
                "TARGET_ITEM_NOT_FOUND" -> SendInterestResult.TargetItemNotFound
                "CANNOT_INTEREST_OWN_ITEM" -> SendInterestResult.CannotInterestOwnItem
                "TARGET_ITEM_NOT_ELIGIBLE" -> SendInterestResult.TargetItemNotEligible
                "PAIR_ALREADY_MATCHED" -> SendInterestResult.PairAlreadyMatched
                else -> SendInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Aksi gagal"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SendInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun cancelOutgoingInterest(
        userId: Long,
        interestId: Long
    ): CancelInterestResult {
        return try {
            val params = JSONObject().apply {
                put("p_interest_id", interestId)
                put("p_sender_user_id", userId)
            }
            val res = client.rpc("rpc_cancel_outgoing_interest", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    val receiverUserId = res.optLong("receiverUserId", 0L)
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    if (receiverUserId > 0L) {
                        notifyUserViaRealtime(receiverUserId, "interest_updated")
                    }
                    CancelInterestResult.Success
                }
                "INTEREST_NOT_FOUND" -> CancelInterestResult.InterestNotFound
                "NOT_INTEREST_OWNER" -> CancelInterestResult.NotInterestOwner
                "INTEREST_ALREADY_PROCESSED" -> {
                    val status = try {
                        InterestStatus.valueOf(res.optString("currentStatus", "ACCEPTED_MATCHED"))
                    } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
                        InterestStatus.ACCEPTED_MATCHED
                    }
                    CancelInterestResult.InterestAlreadyProcessed(status)
                }
                else -> CancelInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Gagal membatalkan minat"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CancelInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun convertPassToInterest(userId: Long, targetItemId: Long): SendInterestResult {
        return sendInterest(userId, targetItemId)
    }

    override suspend fun restorePassToDiscover(userId: Long, targetItemId: Long): Boolean {
        return try {
            val resp = client.rpc(
                "rpc_restore_pass_to_discover",
                JSONObject().put("p_target_item_id", targetItemId)
            )
            if (resp.optString("code") == "SUCCESS") {
                invalidator.invalidateDiscovery()
                true
            } else {
                false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun respondToInterestAndCreateMatch(
        actorUserId: Long,
        interestId: Long,
        chosenSenderItemId: Long
    ): RespondInterestResult {
        return try {
            val params = JSONObject().apply {
                put("p_actor_user_id", actorUserId)
                put("p_interest_id", interestId)
                put("p_chosen_sender_item_id", chosenSenderItemId)
            }
            val res = client.rpc("rpc_respond_to_interest_and_create_match", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    val matchId = res.optLong("matchId")
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    invalidator.invalidateWardrobe()
                    invalidator.invalidateChat()
                    notifyMatchParticipantsViaRealtime(
                        matchId = matchId,
                        user1Id = res.optLong("user1Id", 0L),
                        user2Id = res.optLong("user2Id", 0L)
                    )
                    RespondInterestResult.Success(matchId)
                }
                "INTEREST_ALREADY_PROCESSED_OR_INVALID" -> RespondInterestResult.InterestAlreadyProcessedOrInvalid
                "TARGET_ITEM_NOT_FOUND" -> RespondInterestResult.TargetItemNotFound(res.optBoolean("invalidated", false))
                "INVALID_ITEM_OWNERSHIP" -> RespondInterestResult.InvalidItemOwnership
                "TARGET_ITEM_UNAVAILABLE" -> RespondInterestResult.TargetItemUnavailable(res.optBoolean("invalidated", false))
                "CHOSEN_ITEM_NOT_FOUND" -> RespondInterestResult.ChosenItemNotFound
                "CHOSEN_ITEM_UNAVAILABLE" -> RespondInterestResult.ChosenItemUnavailable
                "BARTER_NOT_ALLOWED" -> RespondInterestResult.BarterNotAllowed
                "ITEM_ALREADY_IN_ACTIVE_MATCH" -> RespondInterestResult.ItemAlreadyInActiveMatch
                "PAIR_ALREADY_MATCHED" -> RespondInterestResult.PairAlreadyMatched
                else -> RespondInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Respon gagal"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            RespondInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun declineIncomingInterest(
        actorUserId: Long,
        interestId: Long
    ): DeclineInterestResult {
        return try {
            val params = JSONObject().apply {
                put("p_actor_user_id", actorUserId)
                put("p_interest_id", interestId)
            }
            val res = client.rpc("rpc_decline_incoming_interest", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    val senderUserId = res.optLong("senderUserId", 0L)
                    invalidator.invalidateNotifications()
                    invalidator.invalidateDiscovery()
                    if (senderUserId > 0L) {
                        notifyUserViaRealtime(senderUserId, "interest_updated")
                    }
                    DeclineInterestResult.Success
                }
                "INTEREST_ALREADY_PROCESSED_OR_INVALID" -> DeclineInterestResult.InterestAlreadyProcessedOrInvalid
                else -> DeclineInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Tolak tawaran gagal"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DeclineInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun cancelMatch(matchId: Long, actorUserId: Long): CancelMatchResult {
        return try {
            val params = JSONObject().apply {
                put("p_match_id", matchId)
                put("p_actor_user_id", actorUserId)
            }
            val res = client.rpc("rpc_cancel_match", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    invalidator.invalidateWardrobe()
                    invalidator.invalidateChat()
                    notifyMatchParticipantsViaRealtime(
                        matchId = matchId,
                        user1Id = res.optLong("user1Id", 0L),
                        user2Id = res.optLong("user2Id", 0L)
                    )
                    CancelMatchResult.Success
                }
                "MATCH_NOT_FOUND" -> CancelMatchResult.MatchNotFound
                "NOT_MATCH_PARTICIPANT" -> CancelMatchResult.NotMatchParticipant
                "MATCH_NOT_ACTIVE" -> {
                    val status = try {
                        MatchStatus.valueOf(res.optString("currentStatus", "ACTIVE"))
                    } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
                        MatchStatus.ACTIVE
                    }
                    CancelMatchResult.MatchNotActive(status)
                }
                else -> CancelMatchResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Batal gagal"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CancelMatchResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun completeMatchExchange(
        matchId: Long,
        actorUserId: Long,
        meetingLocation: String?
    ): CompleteMatchResult {
        return try {
            val params = JSONObject().apply {
                put("p_match_id", matchId)
                put("p_actor_user_id", actorUserId)
                put("p_meeting_location", meetingLocation ?: "")
            }
            val res = client.rpc("rpc_complete_match_exchange", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    invalidator.invalidateWardrobe()
                    invalidator.invalidateChat()
                    notifyMatchParticipantsViaRealtime(
                        matchId = matchId,
                        user1Id = res.optLong("user1Id", 0L),
                        user2Id = res.optLong("user2Id", 0L)
                    )
                    CompleteMatchResult.Success
                }
                "MATCH_NOT_FOUND" -> CompleteMatchResult.MatchNotFound
                "NOT_MATCH_PARTICIPANT" -> CompleteMatchResult.NotMatchParticipant
                "MATCH_NOT_ACTIVE" -> {
                    val status = try {
                        MatchStatus.valueOf(res.optString("currentStatus", "ACTIVE"))
                    } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
                        MatchStatus.ACTIVE
                    }
                    CompleteMatchResult.MatchNotActive(status)
                }
                "ITEMS_NO_LONGER_AVAILABLE" -> CompleteMatchResult.ItemsNoLongerAvailable
                else -> CompleteMatchResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Selesai barter gagal"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CompleteMatchResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    private fun notifyUserViaRealtime(targetUserId: Long, event: String) {
        if (targetUserId <= 0L) return
        val rt = realtimeClient ?: return
        try {
            rt.broadcast(
                "realtime:user_threads_$targetUserId",
                event,
                JSONObject().put("type", "sync")
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    private fun notifyMatchParticipantsViaRealtime(matchId: Long, user1Id: Long, user2Id: Long) {
        val rt = realtimeClient ?: return
        try {
            val payload = JSONObject().put("type", "sync")
            rt.broadcast("realtime:match_$matchId", "match_updated", payload)
            if (user1Id > 0L) rt.broadcast("realtime:user_threads_$user1Id", "thread_updated", payload)
            if (user2Id > 0L) rt.broadcast("realtime:user_threads_$user2Id", "thread_updated", payload)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    override suspend fun markNotificationsRead(userId: Long) {
        try {
            val resp = client.rpc("rpc_mark_notifications_read", JSONObject())
            if (resp.optString("code") == "SUCCESS") {
                invalidator.invalidateNotifications()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    override suspend fun markNotificationReadById(notificationId: Long) {
        try {
            val resp = client.rpc(
                "rpc_mark_notification_read_by_id",
                JSONObject().put("p_notification_id", notificationId)
            )
            if (resp.optString("code") == "SUCCESS") {
                invalidator.invalidateNotifications()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val CONNECTION_ERROR_MESSAGE = "Terjadi gangguan koneksi ke server. Silakan coba lagi."
    }
}

internal fun shouldRefreshDiscoveryForRealtime(userTopic: String, incomingTopic: String, payload: JSONObject): Boolean {
    val event = payload.optString("broadcastEvent")
    return event != "chat_sync" && event != "thread_sync" &&
        (incomingTopic == userTopic || incomingTopic.startsWith("realtime:match_"))
}
