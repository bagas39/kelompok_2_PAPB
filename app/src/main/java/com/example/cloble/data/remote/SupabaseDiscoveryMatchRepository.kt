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
import com.example.cloble.domain.util.DistanceCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
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

    private data class ActiveMatchContext(
        val activeItemIds: Set<Long>,
        val otherActiveItemIds: Set<Long>,
        val activePartnerUserIds: Set<Long>
    )

    private suspend fun fetchActiveMatchContext(): ActiveMatchContext {
        val activeItems = mutableSetOf<Long>()
        val otherActiveItems = mutableSetOf<Long>()
        val activePartners = mutableSetOf<Long>()
        try {
            val ctx = client.rpc("rpc_get_active_match_context", JSONObject())
            ctx.optJSONArray("activeItemIds")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val id = arr.optLong(i)
                    if (id > 0L) activeItems.add(id)
                }
            }
            ctx.optJSONArray("otherActiveItemIds")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val id = arr.optLong(i)
                    if (id > 0L) otherActiveItems.add(id)
                }
            }
            ctx.optJSONArray("activePartnerUserIds")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val id = arr.optLong(i)
                    if (id > 0L) activePartners.add(id)
                }
            }
        } catch (_: Exception) {
        }
        return ActiveMatchContext(activeItems, otherActiveItems, activePartners)
    }

    override fun observeDiscoverCandidates(
        viewerUserId: Long,
        filter: DiscoverFilter
    ): Flow<List<DiscoverCandidate>> =
        combine(
            invalidator.discoveryTrigger.onStart { emit(Unit) },
            invalidator.userTrigger.onStart { emit(Unit) }
        ) { _, _ ->
            fetchDiscoverCandidates(viewerUserId, filter)
        }.flowOn(Dispatchers.IO)

    private suspend fun fetchDiscoverCandidates(
        viewerUserId: Long,
        filter: DiscoverFilter
    ): List<DiscoverCandidate> {
        return try {
            val viewerJson = client.getJsonObjectOrNull("users?id=eq.$viewerUserId") ?: return emptyList()
            val viewerCity = viewerJson.optString("city", "Bandung, Jawa Barat")
            val (viewerLat, viewerLon) = DistanceCalculator.resolveEffectiveCoordinates(
                city = viewerCity,
                latitude = viewerJson.optDouble("latitude", -6.9175),
                longitude = viewerJson.optDouble("longitude", 107.6191)
            )
            val prefRadius = viewerJson.optInt("preferredRadiusKm", 10)
            val maxRadius = filter.maxRadiusKm ?: prefRadius

            val matchContext = fetchActiveMatchContext()
            val activeItemIds = matchContext.activeItemIds
            val activePartnerUserIds = matchContext.activePartnerUserIds

            val passesArr = client.getJsonArray("swipe_passes?userId=eq.$viewerUserId&select=targetItemId")
            val passedItemIds = mutableSetOf<Long>()
            for (i in 0 until passesArr.length()) {
                passedItemIds.add(passesArr.getJSONObject(i).optLong("targetItemId"))
            }

            val myMatchesArr = try {
                client.getJsonArray(
                    "matches?or=(user1Id.eq.$viewerUserId,user2Id.eq.$viewerUserId)&select=id,sourceInterestId,user1ItemId,user2ItemId,status"
                )
            } catch (_: Exception) {
                org.json.JSONArray()
            }
            val nonCancelledMatchSourceIds = mutableSetOf<Long>()
            val nonCancelledMatchItemIds = mutableSetOf<Long>()
            for (i in 0 until myMatchesArr.length()) {
                val m = myMatchesArr.getJSONObject(i)
                val status = m.optString("status")
                if (status == "ACTIVE" || status == "COMPLETED") {
                    nonCancelledMatchSourceIds.add(m.optLong("sourceInterestId"))
                    nonCancelledMatchItemIds.add(m.optLong("user1ItemId"))
                    nonCancelledMatchItemIds.add(m.optLong("user2ItemId"))
                }
            }

            val interestsArr = client.getJsonArray("interests?senderUserId=eq.$viewerUserId&select=id,targetItemId,status")
            val interestedItemIds = mutableSetOf<Long>()
            for (i in 0 until interestsArr.length()) {
                val row = interestsArr.getJSONObject(i)
                val iid = row.optLong("id")
                val targetId = row.optLong("targetItemId")
                val status = row.optString("status")
                val stillBlocking = status == "PENDING" ||
                        status == "DECLINED" ||
                        ((status == "ACCEPTED_MATCHED" || status == "RESOLVED_BY_MATCH") &&
                                (nonCancelledMatchSourceIds.contains(iid) || nonCancelledMatchItemIds.contains(targetId)))
                if (stillBlocking) {
                    interestedItemIds.add(targetId)
                }
            }

            val rawItemsArr = client.getJsonArray(
                "clothing_items?select=*,users:ownerId(id,name,city,latitude,longitude,avatarUri)" +
                        "&status=eq.AVAILABLE&isBarterAllowed=eq.true&ownerId=neq.$viewerUserId&order=createdAt.desc"
            )

            val ratingSumsByOwner = mutableMapOf<Long, Pair<Double, Int>>()
            try {
                val reviewsArr = client.getJsonArray("reviews?select=revieweeId,rating")
                for (r in 0 until reviewsArr.length()) {
                    val rev = reviewsArr.getJSONObject(r)
                    val rid = rev.optLong("revieweeId")
                    val rating = rev.optDouble("rating", 0.0)
                    val prev = ratingSumsByOwner[rid]
                    if (prev == null) {
                        ratingSumsByOwner[rid] = rating to 1
                    } else {
                        ratingSumsByOwner[rid] = (prev.first + rating) to (prev.second + 1)
                    }
                }
            } catch (_: Exception) {
            }

            val candidates = mutableListOf<DiscoverCandidate>()
            for (i in 0 until rawItemsArr.length()) {
                val raw = rawItemsArr.getJSONObject(i)
                val itemId = raw.optLong("id")
                if (activeItemIds.contains(itemId)) continue
                if (passedItemIds.contains(itemId)) continue
                if (interestedItemIds.contains(itemId)) continue

                val ownerJson = raw.optJSONObject("users") ?: JSONObject()
                val ownerId = ownerJson.optLong("id", raw.optLong("ownerId"))
                if (activePartnerUserIds.contains(ownerId)) continue
                val ownerCity = ownerJson.optString("city", "Bandung")
                val (ownerLat, ownerLon) = DistanceCalculator.resolveEffectiveCoordinates(
                    city = ownerCity,
                    latitude = ownerJson.optDouble("latitude", Double.NaN),
                    longitude = ownerJson.optDouble("longitude", Double.NaN)
                )

                val distanceKm = DistanceCalculator.calculateKm(
                    lat1 = viewerLat,
                    lon1 = viewerLon,
                    lat2 = ownerLat,
                    lon2 = ownerLon
                )

                if (distanceKm > maxRadius) continue

                val category = raw.optString("category")
                val size = raw.optString("size")
                val gender = raw.optString("gender")
                val color = raw.optString("color")

                if (filter.selectedCategories.isNotEmpty() && !filter.selectedCategories.contains(category)) continue
                if (filter.selectedSizes.isNotEmpty() && !filter.selectedSizes.contains(size)) continue
                if (filter.selectedColors.isNotEmpty() && !filter.selectedColors.contains(color)) continue
                if (filter.selectedGenders.isNotEmpty()) {
                    val matchGender = filter.selectedGenders.contains(gender) ||
                            filter.selectedGenders.contains("Uniseks") ||
                            gender.equals("Uniseks", ignoreCase = true)
                    if (!matchGender) continue
                }

                val stats = ratingSumsByOwner[ownerId]
                val count = stats?.second ?: 0
                val avg = if (count > 0 && stats != null) stats.first / count else 0.0

                candidates.add(SupabaseMappers.parseDiscoverCandidate(raw, distanceKm, avg, count))
            }
            candidates
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun observeMatchDetail(
        matchId: Long,
        viewerUserId: Long
    ): Flow<VerifiedMatchDetail?> = channelFlow {
        val refreshTrigger = Channel<Unit>(Channel.CONFLATED)

        if (cacheDao != null) {
            val initialSyncDone = AtomicBoolean(false)
            val hasEmittedCache = AtomicBoolean(false)

            val cacheJob = launch {
                cacheDao.observeMatchDetail(viewerUserId, matchId).collect { entity ->
                    if (entity != null) {
                        hasEmittedCache.set(true)
                        send(OfflineChatCacheMappers.toDomain(entity))
                    } else if (initialSyncDone.get()) {
                        send(null)
                    }
                }
            }

            val syncJob = launch {
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

            refreshTrigger.trySend(Unit)

            val discoveryJob = launch {
                invalidator.discoveryTrigger.collect { refreshTrigger.trySend(Unit) }
            }
            val chatJob = launch {
                invalidator.chatTrigger.collect { refreshTrigger.trySend(Unit) }
            }

            awaitClose {
                refreshTrigger.close()
                cacheJob.cancel()
                syncJob.cancel()
                discoveryJob.cancel()
                chatJob.cancel()
            }
        } else {
            var lastKnownDetail: VerifiedMatchDetail? = null
            var hasEmitted = false

            val syncJob = launch {
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

            refreshTrigger.trySend(Unit)

            val discoveryJob = launch {
                invalidator.discoveryTrigger.collect { refreshTrigger.trySend(Unit) }
            }
            val chatJob = launch {
                invalidator.chatTrigger.collect { refreshTrigger.trySend(Unit) }
            }

            awaitClose {
                refreshTrigger.close()
                syncJob.cancel()
                discoveryJob.cancel()
                chatJob.cancel()
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchMatchDetailResult(
        matchId: Long,
        viewerUserId: Long
    ): RemoteMatchDetailResult {
        return try {
            val matchArr = client.getJsonArray(
                "matches?id=eq.$matchId&select=*,u1:user1Id(name,city,avatarUri),u2:user2Id(name,city,avatarUri)," +
                        "i1:user1ItemId(*),i2:user2ItemId(*)"
            )
            if (matchArr.length() == 0) return RemoteMatchDetailResult.NotFoundOrForbidden
            val m = matchArr.getJSONObject(0)
            val user1Id = m.optLong("user1Id")
            val user2Id = m.optLong("user2Id")
            if (viewerUserId != user1Id && viewerUserId != user2Id) {
                return RemoteMatchDetailResult.NotFoundOrForbidden
            }

            val isViewerUser1 = viewerUserId == user1Id
            val partnerUserId = if (isViewerUser1) user2Id else user1Id
            val partnerObj = if (isViewerUser1) m.optJSONObject("u2") else m.optJSONObject("u1")
            val partnerName = partnerObj?.optString("name", "Pengguna") ?: "Pengguna"
            val partnerCity = partnerObj?.optString("city", "Bandung") ?: "Bandung"
            val partnerAvatarUri = partnerObj?.optString("avatarUri")
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

            var partnerAvg = 0.0
            var partnerCount = 0
            try {
                val reviewsArr = client.getJsonArray("reviews?revieweeId=eq.$partnerUserId&select=rating")
                partnerCount = reviewsArr.length()
                if (partnerCount > 0) {
                    var sum = 0.0
                    for (i in 0 until partnerCount) {
                        sum += reviewsArr.getJSONObject(i).optDouble("rating", 0.0)
                    }
                    partnerAvg = sum / partnerCount
                }
            } catch (_: Exception) {
            }

            val myItemObj = if (isViewerUser1) m.optJSONObject("i1") else m.optJSONObject("i2")
            val partnerItemObj = if (isViewerUser1) m.optJSONObject("i2") else m.optJSONObject("i1")

            val myItem = myItemObj?.let { SupabaseMappers.parseClothingItem(it) }
                ?: return RemoteMatchDetailResult.NotFoundOrForbidden
            val partnerItem = partnerItemObj?.let { SupabaseMappers.parseClothingItem(it) }
                ?: return RemoteMatchDetailResult.NotFoundOrForbidden

            val matchStatus = try {
                MatchStatus.valueOf(m.optString("status", "ACTIVE"))
            } catch (_: Exception) {
                MatchStatus.ACTIVE
            }

            RemoteMatchDetailResult.Found(
                VerifiedMatchDetail(
                    matchId = matchId,
                    status = matchStatus,
                    meetingLocation = m.optString("meetingLocation").takeIf {
                        it.isNotBlank() && !it.equals("null", ignoreCase = true)
                    },
                    createdAt = m.optLong("createdAt"),
                    updatedAt = m.optLong("updatedAt"),
                    myUserId = viewerUserId,
                    partnerUserId = partnerUserId,
                    partnerName = partnerName,
                    partnerCity = partnerCity,
                    partnerAvatarUri = partnerAvatarUri,
                    partnerAverageRating = partnerAvg,
                    partnerReviewCount = partnerCount,
                    myItem = myItem,
                    partnerItem = partnerItem
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            RemoteMatchDetailResult.NetworkError
        }
    }

    override fun observeNotifications(userId: Long): Flow<List<NotificationFeedItem>> =
        invalidator.notificationsTrigger
            .onStart { emit(Unit) }
            .map { fetchNotifications(userId) }
            .flowOn(Dispatchers.IO)

    private suspend fun fetchNotifications(userId: Long): List<NotificationFeedItem> {
        return try {
            val ctx = fetchActiveMatchContext()
            val reviewedMatchIds = mutableSetOf<Long>()
            try {
                val revArr = client.getJsonArray("reviews?reviewerId=eq.$userId&select=matchId")
                for (i in 0 until revArr.length()) {
                    val mid = revArr.getJSONObject(i).optLong("matchId")
                    if (mid > 0L) reviewedMatchIds.add(mid)
                }
            } catch (_: Exception) {
            }

            val arr = client.getJsonArray(
                "notifications?userId=eq.$userId&select=*,interests:relatedInterestId(*,users:senderUserId(id,name),clothing_items:targetItemId(id,name,status,isBarterAllowed,mainPhotoUri)),matches:relatedMatchId(*,user1:user1Id(id,name),user2:user2Id(id,name))&order=createdAt.desc"
            )
            val list = mutableListOf<NotificationFeedItem>()
            for (i in 0 until arr.length()) {
                list.add(
                    SupabaseMappers.parseNotification(
                        json = arr.getJSONObject(i),
                        activeItemIds = ctx.activeItemIds,
                        activePartnerUserIds = ctx.activePartnerUserIds,
                        reviewedMatchIds = reviewedMatchIds
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun observeUnreadNotificationCount(userId: Long): Flow<Int> =
        invalidator.notificationsTrigger
            .onStart { emit(Unit) }
            .map { fetchUnreadNotificationCount(userId) }
            .flowOn(Dispatchers.IO)

    private suspend fun fetchUnreadNotificationCount(userId: Long): Int {
        return try {
            val arr = client.getJsonArray("notifications?userId=eq.$userId&isRead=eq.false&select=id")
            arr.length()
        } catch (_: Exception) {
            0
        }
    }

    override fun observeOutgoingInterests(userId: Long): Flow<List<OutgoingInterestSummary>> =
        invalidator.discoveryTrigger
            .onStart { emit(Unit) }
            .map { fetchOutgoingInterests(userId) }
            .flowOn(Dispatchers.IO)

    private suspend fun fetchOutgoingInterests(userId: Long): List<OutgoingInterestSummary> {
        return try {
            val arr = client.getJsonArray(
                "interests?senderUserId=eq.$userId&select=*,clothing_items:targetItemId(*,users:ownerId(*)),matches(*)&order=createdAt.desc"
            )
            val myMatchesArr = try {
                client.getJsonArray(
                    "matches?or=(user1Id.eq.$userId,user2Id.eq.$userId)&select=id,sourceInterestId,user1Id,user2Id,user1ItemId,user2ItemId,status,createdAt&order=updatedAt.desc"
                )
            } catch (_: Exception) {
                org.json.JSONArray()
            }
            val otherActiveItemIds = fetchActiveMatchContext().otherActiveItemIds

            val list = mutableListOf<OutgoingInterestSummary>()
            for (i in 0 until arr.length()) {
                val raw = arr.getJSONObject(i)
                val interestId = raw.optLong("id")
                val targetItemId = raw.optLong("targetItemId")
                val targetItemJson = raw.optJSONObject("clothing_items")
                val ownerId = targetItemJson?.optLong("ownerId") ?: -1L
                val itemStatus = try {
                    ItemStatus.valueOf(targetItemJson?.optString("status", "AVAILABLE") ?: "AVAILABLE")
                } catch (_: Exception) {
                    ItemStatus.AVAILABLE
                }
                val interestStatus = try {
                    InterestStatus.valueOf(raw.optString("status"))
                } catch (_: Exception) {
                    InterestStatus.PENDING
                }

                var resolvedMatchId: Long? = null
                var resolvedMatchStatus: MatchStatus? = null
                val directMatchArr = raw.optJSONArray("matches")
                if (directMatchArr != null && directMatchArr.length() > 0) {
                    val dm = directMatchArr.optJSONObject(0)
                    if (dm != null) {
                        resolvedMatchId = dm.optLong("id")
                        resolvedMatchStatus = try {
                            MatchStatus.valueOf(dm.optString("status", "ACTIVE"))
                        } catch (_: Exception) {
                            null
                        }
                    }
                }

                if (resolvedMatchId == null && (interestStatus == InterestStatus.ACCEPTED_MATCHED || interestStatus == InterestStatus.RESOLVED_BY_MATCH)) {
                    for (j in 0 until myMatchesArr.length()) {
                        val mm = myMatchesArr.getJSONObject(j)
                        if (mm.optLong("sourceInterestId") == interestId) {
                            resolvedMatchId = mm.optLong("id")
                            resolvedMatchStatus = try {
                                MatchStatus.valueOf(mm.optString("status", "ACTIVE"))
                            } catch (_: Exception) {
                                null
                            }
                            break
                        }
                    }
                    if (resolvedMatchId == null) {
                        val interestCreatedAt = raw.optLong("createdAt")
                        var bestScore = Long.MAX_VALUE
                        for (j in 0 until myMatchesArr.length()) {
                            val mm = myMatchesArr.getJSONObject(j)
                            val mUser1 = mm.optLong("user1Id")
                            val mUser2 = mm.optLong("user2Id")
                            val mItem1 = mm.optLong("user1ItemId")
                            val mItem2 = mm.optLong("user2ItemId")
                            val matchesByItemAndOwner = (mItem1 == targetItemId || mItem2 == targetItemId) &&
                                    ((mUser1 == userId && mUser2 == ownerId) || (mUser2 == userId && mUser1 == ownerId))
                            if (matchesByItemAndOwner) {
                                val diff = kotlin.math.abs(mm.optLong("createdAt") - interestCreatedAt)
                                if (diff < bestScore) {
                                    bestScore = diff
                                    resolvedMatchId = mm.optLong("id")
                                    resolvedMatchStatus = try {
                                        MatchStatus.valueOf(mm.optString("status", "ACTIVE"))
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }
                        }
                    }
                }

                val isLockedByOther = resolvedMatchId == null && (
                        interestStatus == InterestStatus.RESOLVED_BY_MATCH ||
                                interestStatus == InterestStatus.INVALIDATED ||
                                itemStatus != ItemStatus.AVAILABLE ||
                                otherActiveItemIds.contains(targetItemId)
                        )

                list.add(
                    SupabaseMappers.parseOutgoingInterest(
                        json = raw,
                        resolvedMatchId = resolvedMatchId,
                        resolvedMatchStatus = resolvedMatchStatus,
                        isLockedByOtherMatch = isLockedByOther
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun observePassedClothing(userId: Long): Flow<List<PassedClothingSummary>> =
        invalidator.discoveryTrigger
            .onStart { emit(Unit) }
            .map { fetchPassedClothing(userId) }
            .flowOn(Dispatchers.IO)

    private suspend fun fetchPassedClothing(userId: Long): List<PassedClothingSummary> {
        return try {
            val arr = client.getJsonArray(
                "swipe_passes?userId=eq.$userId&select=*,clothing_items:targetItemId(*,users:ownerId(*))&order=createdAt.desc"
            )
            val activeItemIds = fetchActiveMatchContext().activeItemIds

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
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun passCandidate(userId: Long, targetItemId: Long) {
        try {
            val body = JSONObject().apply {
                put("userId", userId)
                put("targetItemId", targetItemId)
                put("createdAt", System.currentTimeMillis())
            }
            client.post("swipe_passes", body.toString())
            invalidator.invalidateDiscovery()
        } catch (_: Exception) {
        }
    }

    override suspend fun sendInterest(senderId: Long, targetItemId: Long): SendInterestResult {
        return try {
            val params = JSONObject().apply {
                put("p_sender_user_id", senderId)
                put("p_target_item_id", targetItemId)
                put("p_now_millis", System.currentTimeMillis())
                put("p_delete_swipe_pass", true)
            }
            val res = client.rpc("rpc_send_interest", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    val isMutual = res.optBoolean("isMutualMatch", false)
                    val matchId = if (isMutual && !res.isNull("matchId")) res.optLong("matchId") else null
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    if (matchId != null) {
                        invalidator.invalidateChat()
                        invalidator.invalidateWardrobe()
                        notifyMatchParticipantsViaRealtime(matchId)
                    }
                    SendInterestResult.Success(res.optLong("interestId"), matchId)
                }
                "SENDER_NOT_FOUND" -> SendInterestResult.InvalidOrMissingSenderSession
                "ALREADY_INTERESTED" -> {
                    val status = try {
                        InterestStatus.valueOf(res.optString("existingStatus", "PENDING"))
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
        } catch (e: Exception) {
            SendInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
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
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    CancelInterestResult.Success
                }
                "INTEREST_NOT_FOUND" -> CancelInterestResult.InterestNotFound
                "NOT_INTEREST_OWNER" -> CancelInterestResult.NotInterestOwner
                "INTEREST_ALREADY_PROCESSED" -> {
                    val status = try {
                        InterestStatus.valueOf(res.optString("currentStatus", "ACCEPTED_MATCHED"))
                    } catch (_: Exception) {
                        InterestStatus.ACCEPTED_MATCHED
                    }
                    CancelInterestResult.InterestAlreadyProcessed(status)
                }
                else -> CancelInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Gagal membatalkan minat"))
            }
        } catch (e: Exception) {
            CancelInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
        }
    }

    override suspend fun convertPassToInterest(userId: Long, targetItemId: Long): SendInterestResult {
        return sendInterest(userId, targetItemId)
    }

    override suspend fun restorePassToDiscover(userId: Long, targetItemId: Long): Boolean {
        return try {
            client.delete("swipe_passes?userId=eq.$userId&targetItemId=eq.$targetItemId")
            invalidator.invalidateDiscovery()
            true
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
                put("p_now_millis", System.currentTimeMillis())
            }
            val res = client.rpc("rpc_respond_to_interest_and_create_match", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    val matchId = res.optLong("matchId")
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    invalidator.invalidateWardrobe()
                    invalidator.invalidateChat()
                    notifyMatchParticipantsViaRealtime(matchId)
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
        } catch (e: Exception) {
            RespondInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
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
                put("p_now_millis", System.currentTimeMillis())
            }
            val res = client.rpc("rpc_decline_incoming_interest", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    invalidator.invalidateNotifications()
                    invalidator.invalidateDiscovery()
                    DeclineInterestResult.Success
                }
                "INTEREST_ALREADY_PROCESSED_OR_INVALID" -> DeclineInterestResult.InterestAlreadyProcessedOrInvalid
                else -> DeclineInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Tolak tawaran gagal"))
            }
        } catch (e: Exception) {
            DeclineInterestResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
        }
    }

    override suspend fun cancelMatch(matchId: Long, actorUserId: Long): CancelMatchResult {
        return try {
            val params = JSONObject().apply {
                put("p_match_id", matchId)
                put("p_actor_user_id", actorUserId)
                put("p_now_millis", System.currentTimeMillis())
            }
            val res = client.rpc("rpc_cancel_match", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    invalidator.invalidateWardrobe()
                    invalidator.invalidateChat()
                    notifyMatchParticipantsViaRealtime(matchId)
                    CancelMatchResult.Success
                }
                "MATCH_NOT_FOUND" -> CancelMatchResult.MatchNotFound
                "NOT_MATCH_PARTICIPANT" -> CancelMatchResult.NotMatchParticipant
                "MATCH_NOT_ACTIVE" -> {
                    val status = try {
                        MatchStatus.valueOf(res.optString("currentStatus", "ACTIVE"))
                    } catch (_: Exception) {
                        MatchStatus.ACTIVE
                    }
                    CancelMatchResult.MatchNotActive(status)
                }
                else -> CancelMatchResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Batal gagal"))
            }
        } catch (e: Exception) {
            CancelMatchResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
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
                put("p_now_millis", System.currentTimeMillis())
            }
            val res = client.rpc("rpc_complete_match_exchange", params)
            when (res.optString("code")) {
                "SUCCESS" -> {
                    invalidator.invalidateDiscovery()
                    invalidator.invalidateNotifications()
                    invalidator.invalidateWardrobe()
                    invalidator.invalidateChat()
                    notifyMatchParticipantsViaRealtime(matchId)
                    CompleteMatchResult.Success
                }
                "MATCH_NOT_FOUND" -> CompleteMatchResult.MatchNotFound
                "NOT_MATCH_PARTICIPANT" -> CompleteMatchResult.NotMatchParticipant
                "MATCH_NOT_ACTIVE" -> {
                    val status = try {
                        MatchStatus.valueOf(res.optString("currentStatus", "ACTIVE"))
                    } catch (_: Exception) {
                        MatchStatus.ACTIVE
                    }
                    CompleteMatchResult.MatchNotActive(status)
                }
                "ITEMS_NO_LONGER_AVAILABLE" -> CompleteMatchResult.ItemsNoLongerAvailable
                else -> CompleteMatchResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Selesai barter gagal"))
            }
        } catch (e: Exception) {
            CompleteMatchResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
        }
    }

    private suspend fun notifyMatchParticipantsViaRealtime(matchId: Long) {
        val rt = realtimeClient ?: return
        try {
            val matchObj = client.getJsonObjectOrNull("matches?id=eq.$matchId&select=user1Id,user2Id") ?: return
            val u1 = matchObj.optLong("user1Id")
            val u2 = matchObj.optLong("user2Id")
            val payload = JSONObject().put("matchId", matchId)
            if (u1 > 0L) rt.broadcast("realtime:user_threads_$u1", "thread_updated", payload)
            if (u2 > 0L) rt.broadcast("realtime:user_threads_$u2", "thread_updated", payload)
        } catch (_: Exception) {
        }
    }

    override suspend fun markNotificationsRead(userId: Long) {
        try {
            val body = JSONObject().put("isRead", true)
            client.patch("notifications?userId=eq.$userId&isRead=eq.false", body.toString())
            invalidator.invalidateNotifications()
        } catch (_: Exception) {
        }
    }

    override suspend fun markNotificationReadById(notificationId: Long) {
        try {
            val body = JSONObject().put("isRead", true)
            client.patch("notifications?id=eq.$notificationId", body.toString())
            invalidator.invalidateNotifications()
        } catch (_: Exception) {
        }
    }
}
