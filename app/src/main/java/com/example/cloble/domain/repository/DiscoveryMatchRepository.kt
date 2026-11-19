package com.example.cloble.domain.repository

import com.example.cloble.domain.model.DiscoverCandidate
import com.example.cloble.domain.model.DiscoverFilter
import com.example.cloble.domain.model.NotificationFeedItem
import com.example.cloble.domain.model.OutgoingInterestSummary
import com.example.cloble.domain.model.PassedClothingSummary
import com.example.cloble.domain.model.VerifiedMatchDetail
import com.example.cloble.domain.result.CancelInterestResult
import com.example.cloble.domain.result.CancelMatchResult
import com.example.cloble.domain.result.CompleteMatchResult
import com.example.cloble.domain.result.RespondInterestResult
import com.example.cloble.domain.result.SendInterestResult
import kotlinx.coroutines.flow.Flow

interface DiscoveryMatchRepository {
    fun observeDiscoverCandidates(viewerUserId: Long, filter: DiscoverFilter): Flow<List<DiscoverCandidate>>
    fun observeMatchDetail(matchId: Long, viewerUserId: Long): Flow<VerifiedMatchDetail?>
    fun observeNotifications(userId: Long): Flow<List<NotificationFeedItem>>
    fun observeUnreadNotificationCount(userId: Long): Flow<Int>
    fun observeOutgoingInterests(userId: Long): Flow<List<OutgoingInterestSummary>>
    fun observePassedClothing(userId: Long): Flow<List<PassedClothingSummary>>

    suspend fun passCandidate(userId: Long, targetItemId: Long)
    suspend fun sendInterest(senderId: Long, targetItemId: Long): SendInterestResult
    suspend fun cancelOutgoingInterest(userId: Long, interestId: Long): CancelInterestResult
    suspend fun convertPassToInterest(userId: Long, targetItemId: Long): SendInterestResult
    suspend fun restorePassToDiscover(userId: Long, targetItemId: Long): Boolean
    suspend fun respondToInterestAndCreateMatch(
        actorUserId: Long,
        interestId: Long,
        chosenSenderItemId: Long
    ): RespondInterestResult
    suspend fun cancelMatch(matchId: Long, actorUserId: Long): CancelMatchResult
    suspend fun completeMatchExchange(
        matchId: Long,
        actorUserId: Long,
        meetingLocation: String?
    ): CompleteMatchResult
    suspend fun markNotificationsRead(userId: Long)
    suspend fun markNotificationReadById(notificationId: Long)
}
