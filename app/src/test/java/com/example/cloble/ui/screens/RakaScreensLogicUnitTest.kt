package com.example.cloble.ui.screens

import androidx.lifecycle.SavedStateHandle
import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.ClothingDraft
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.DiscoverCandidate
import com.example.cloble.domain.model.DiscoverFilter
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.NotificationFeedItem
import com.example.cloble.domain.model.OutgoingInterestSummary
import com.example.cloble.domain.model.PassedClothingSummary
import com.example.cloble.domain.model.UserPreferences
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.model.UserProfileUpdate
import com.example.cloble.domain.model.UserReview
import com.example.cloble.domain.model.VerifiedMatchDetail
import com.example.cloble.domain.model.WardrobeItemSummary
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.result.CancelInterestResult
import com.example.cloble.domain.result.CancelMatchResult
import com.example.cloble.domain.result.ChangePasswordResult
import com.example.cloble.domain.result.CompleteMatchResult
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.result.DeclineInterestResult
import com.example.cloble.domain.result.RequestPasswordResetOtpResult
import com.example.cloble.domain.result.ResetPasswordResult
import com.example.cloble.domain.result.RespondInterestResult
import com.example.cloble.domain.result.ReviewSubmitResult
import com.example.cloble.domain.result.SendInterestResult
import com.example.cloble.domain.result.SendMessageResult
import com.example.cloble.domain.result.WardrobeMutationResult
import com.example.cloble.ui.screens.chat.ConversationViewModel
import com.example.cloble.ui.screens.chat.formatChatThreadTimestamp
import com.example.cloble.ui.screens.history.ActivityHistoryEvent
import com.example.cloble.ui.screens.history.ActivityHistoryViewModel
import com.example.cloble.ui.screens.profile.OtherUserProfileViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class RakaScreensLogicUnitTest {

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    private fun sampleProfile(id: Long = 10L, name: String = "Raka"): UserProfile = UserProfile(
        id = id,
        name = name,
        email = "$id@example.com",
        bio = "Bio",
        avatarUri = null,
        city = "Malang",
        latitude = -7.9666,
        longitude = 112.6326,
        preferredSize = "M",
        preferredGender = "Uniseks",
        preferredRadiusKm = 10,
        createdAt = 1700000000000L
    )

    private fun sampleItem(id: Long, ownerId: Long = 20L, name: String = "Hoodie"): ClothingItem = ClothingItem(
        id = id,
        ownerId = ownerId,
        name = name,
        description = "Bagus",
        category = "Luaran",
        size = "M",
        gender = "Uniseks",
        color = "Hitam",
        condition = "Baik",
        mainPhotoUri = null,
        detailPhotoUri = null,
        extraPhotoUri = null,
        isBarterAllowed = true,
        status = ItemStatus.AVAILABLE,
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L
    )

    private fun sampleMatchDetail(matchId: Long = 99L): VerifiedMatchDetail = VerifiedMatchDetail(
        matchId = matchId,
        status = MatchStatus.ACTIVE,
        meetingLocation = null,
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L,
        myUserId = 10L,
        partnerUserId = 20L,
        partnerName = "Rina",
        partnerCity = "Malang",
        partnerAvatarUri = null,
        partnerAverageRating = 5.0,
        partnerReviewCount = 1,
        myItem = sampleItem(1L, ownerId = 10L, name = "Kemeja"),
        partnerItem = sampleItem(2L, ownerId = 20L, name = "Jaket")
    )

    private inner class FakeAuthRepo(private val userId: Long = 10L) : AuthRepository {
        override fun observeLoggedInUserId(): Flow<Long?> = flowOf(userId)
        override fun observeCurrentUser(): Flow<UserProfile?> = flowOf(sampleProfile(userId))
        override suspend fun login(email: String, rawPassword: String): AuthResult = AuthResult.Success(userId)
        override suspend fun requestPasswordResetOtp(email: String): RequestPasswordResetOtpResult =
            RequestPasswordResetOtpResult.Success
        override suspend fun verifyOtpAndResetPassword(email: String, otpCode: String, newRawPassword: String): ResetPasswordResult =
            ResetPasswordResult.Success
        override suspend fun changePassword(currentRawPassword: String, newRawPassword: String): ChangePasswordResult =
            ChangePasswordResult.Success
        override suspend fun register(
            name: String,
            email: String,
            rawPassword: String,
            city: String,
            latitude: Double?,
            longitude: Double?
        ): AuthResult = AuthResult.Success(userId)
        override suspend fun logout() {}
    }

    private class FakeChatRepo : ChatRepository {
        override fun observeChatThreads(userId: Long): Flow<List<ChatThreadSummary>> = flowOf(emptyList())
        override fun observePartnerMatches(userId: Long, partnerUserId: Long): Flow<List<ChatThreadSummary>> = flowOf(emptyList())
        override fun observeMessages(matchId: Long, viewerUserId: Long): Flow<List<ChatMessage>> = flowOf(emptyList())
        override suspend fun sendMessage(matchId: Long, senderId: Long, text: String): SendMessageResult =
            SendMessageResult.Success(1L)
        override suspend fun markMessagesRead(matchId: Long, viewerUserId: Long) {}
    }

    private class FakeProfileReviewRepo(private val profile: UserProfile?) : ProfileReviewRepository {
        override fun observeUserProfile(userId: Long, viewerUserId: Long?): Flow<UserProfile?> = flowOf(profile)
        override fun observeUserReviews(userId: Long): Flow<List<UserReview>> = flowOf(emptyList())
        override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> = flowOf(false)
        override suspend fun updatePreferences(userId: Long, prefs: UserPreferences): Boolean = true
        override suspend fun updateBio(userId: Long, bio: String) {}
        override suspend fun updateProfile(userId: Long, update: UserProfileUpdate): Boolean = true
        override suspend fun persistAvatarPhoto(sourceUriString: String): String? = null
        override suspend fun submitReview(matchId: Long, reviewerId: Long, rating: Int, comment: String): ReviewSubmitResult =
            ReviewSubmitResult.Success(1L)
    }

    private class FakeWardrobeRepo(private val summaries: List<WardrobeItemSummary>) : WardrobeRepository {
        var publicWardrobeObserveCalls = 0
        override fun observeMyWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeUserPublicWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> {
            publicWardrobeObserveCalls++
            return flowOf(summaries)
        }
        override fun observeClothingItem(itemId: Long): Flow<ClothingItem?> = flowOf(null)
        override suspend fun getClothingItem(itemId: Long): ClothingItem? = null
        override suspend fun isItemLocked(itemId: Long): Boolean = false
        override suspend fun addClothingItem(ownerId: Long, draft: ClothingDraft): WardrobeMutationResult =
            WardrobeMutationResult.Success(1L)
        override suspend fun updateClothingItem(actorUserId: Long, itemId: Long, draft: ClothingDraft): WardrobeMutationResult =
            WardrobeMutationResult.Success(itemId)
        override suspend fun setBarterAllowed(actorUserId: Long, itemId: Long, allowed: Boolean): WardrobeMutationResult =
            WardrobeMutationResult.Success(itemId)
        override suspend fun softDeleteClothingItem(actorUserId: Long, itemId: Long): WardrobeMutationResult =
            WardrobeMutationResult.Success(itemId)
        override suspend fun persistLocalPhoto(sourceUriString: String): String? = null
        override suspend fun countEligibleBarterItems(ownerId: Long): Int = 1
    }

    private class FakeDiscoveryMatchRepo(private val detail: VerifiedMatchDetail) : DiscoveryMatchRepository {
        var cancelMatchResult: CancelMatchResult = CancelMatchResult.Success
        var completeMatchResult: CompleteMatchResult = CompleteMatchResult.Success
        var cancelInterestCalls = 0
        var cancelInterestGate: CompletableDeferred<Unit>? = null
        var convertPassResult: SendInterestResult = SendInterestResult.Success(1L)
        var respondInterestResult: RespondInterestResult = RespondInterestResult.Success(1L)

        override fun observeDiscoverCandidates(viewerUserId: Long, filter: DiscoverFilter): Flow<List<DiscoverCandidate>> =
            flowOf(emptyList())
        override fun observeMatchDetail(matchId: Long, viewerUserId: Long): Flow<VerifiedMatchDetail?> =
            flowOf(detail.copy(matchId = matchId))
        override fun observeNotifications(userId: Long): Flow<List<NotificationFeedItem>> = flowOf(emptyList())
        override fun observeUnreadNotificationCount(userId: Long): Flow<Int> = flowOf(0)
        override fun observeOutgoingInterests(userId: Long): Flow<List<OutgoingInterestSummary>> = flowOf(emptyList())
        override fun observePassedClothing(userId: Long): Flow<List<PassedClothingSummary>> = flowOf(emptyList())
        override suspend fun passCandidate(userId: Long, targetItemId: Long): Boolean = true
        override suspend fun sendInterest(senderId: Long, targetItemId: Long): SendInterestResult =
            SendInterestResult.Success(1L)
        override suspend fun cancelOutgoingInterest(userId: Long, interestId: Long): CancelInterestResult {
            cancelInterestCalls++
            cancelInterestGate?.await()
            return CancelInterestResult.Success
        }
        override suspend fun convertPassToInterest(userId: Long, targetItemId: Long): SendInterestResult = convertPassResult
        override suspend fun restorePassToDiscover(userId: Long, targetItemId: Long): Boolean = true
        override suspend fun respondToInterestAndCreateMatch(
            actorUserId: Long,
            interestId: Long,
            chosenSenderItemId: Long
        ): RespondInterestResult = respondInterestResult
        override suspend fun declineIncomingInterest(actorUserId: Long, interestId: Long): DeclineInterestResult =
            DeclineInterestResult.Success
        override suspend fun cancelMatch(matchId: Long, actorUserId: Long): CancelMatchResult = cancelMatchResult
        override suspend fun completeMatchExchange(matchId: Long, actorUserId: Long, meetingLocation: String?): CompleteMatchResult =
            completeMatchResult
        override suspend fun markNotificationsRead(userId: Long) {}
        override suspend fun markNotificationReadById(notificationId: Long) {}
    }

    @Test
    fun conversationViewModel_clearsErrorMessageOnMatchActionsAndPersistsMatchId() = runTest {
        val handle = SavedStateHandle(mapOf("matchId" to 99L))
        val discoveryRepo = FakeDiscoveryMatchRepo(sampleMatchDetail(99L)).apply {
            cancelMatchResult = CancelMatchResult.DatabaseError(DbFailure.UnexpectedDatabaseError("err"))
        }
        val vm = ConversationViewModel(
            savedStateHandle = handle,
            authRepository = FakeAuthRepo(10L),
            discoveryMatchRepository = discoveryRepo,
            chatRepository = FakeChatRepo(),
            profileReviewRepository = FakeProfileReviewRepo(sampleProfile())
        )

        vm.confirmCancelMatch()
        assertNotNull(vm.uiState.value.errorMessage)

        discoveryRepo.cancelMatchResult = CancelMatchResult.Success
        vm.confirmCancelMatch()
        assertNull(vm.uiState.value.errorMessage)

        discoveryRepo.completeMatchResult = CompleteMatchResult.DatabaseError(DbFailure.UnexpectedDatabaseError("err"))
        vm.confirmCompleteExchange()
        assertNotNull(vm.uiState.value.errorMessage)

        discoveryRepo.completeMatchResult = CompleteMatchResult.Success
        vm.confirmCompleteExchange()
        assertNull(vm.uiState.value.errorMessage)

        vm.selectMatch(202L)
        assertEquals(202L, handle.get<Long>("matchId"))
    }

    @Test
    fun activityHistoryViewModel_blocksConcurrentCancelAcrossDifferentIdsAndEmitsMutualMatch() = runTest {
        val gate = CompletableDeferred<Unit>()
        val discoveryRepo = FakeDiscoveryMatchRepo(sampleMatchDetail()).apply {
            cancelInterestGate = gate
            convertPassResult = SendInterestResult.Success(interestId = 50L, matchId = 777L)
        }
        val vm = ActivityHistoryViewModel(
            authRepository = FakeAuthRepo(10L),
            discoveryMatchRepository = discoveryRepo
        )

        val collectedEvents = mutableListOf<ActivityHistoryEvent>()
        val collectorJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { collectedEvents.add(it) }
        }

        vm.cancelOutgoingInterest(101L)
        assertEquals(101L, vm.uiState.value.cancellingInterestId)
        assertNull(vm.uiState.value.actionMessage)

        vm.cancelOutgoingInterest(102L)
        assertEquals(1, discoveryRepo.cancelInterestCalls)

        gate.complete(Unit)
        assertNull(vm.uiState.value.cancellingInterestId)
        assertNotNull(vm.uiState.value.actionMessage)

        vm.convertPassToInterest(55L)
        assertEquals(listOf(ActivityHistoryEvent.MutualMatchCreated(777L)), collectedEvents)
        collectorJob.cancel()
    }

    @Test
    fun otherUserProfileViewModel_mapsItemAvailabilityErrorsAndRefreshesWardrobe() = runTest {
        val candidateItem = sampleItem(20L, ownerId = 20L, name = "Jaket")
        val wardrobeRepo = FakeWardrobeRepo(
            listOf(WardrobeItemSummary(item = candidateItem, isInActiveMatch = false))
        )
        val discoveryRepo = FakeDiscoveryMatchRepo(sampleMatchDetail()).apply {
            respondInterestResult = RespondInterestResult.ChosenItemUnavailable
        }
        val handle = SavedStateHandle(mapOf("userId" to 20L, "replyToInterestId" to 33L))
        val vm = OtherUserProfileViewModel(
            savedStateHandle = handle,
            authRepository = FakeAuthRepo(10L),
            profileReviewRepository = FakeProfileReviewRepo(sampleProfile(20L, "Rina")),
            wardrobeRepository = wardrobeRepo,
            discoveryMatchRepository = discoveryRepo
        )
        val initialWardrobeCalls = wardrobeRepo.publicWardrobeObserveCalls

        vm.selectItem(20L)
        assertEquals(20L, vm.uiState.value.selectedItemId)
        vm.acceptInterestAndCreateMatch()
        assertNull(vm.uiState.value.selectedItemId)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("tidak tersedia"))
        assertTrue(wardrobeRepo.publicWardrobeObserveCalls > initialWardrobeCalls)

        discoveryRepo.respondInterestResult = RespondInterestResult.BarterNotAllowed
        vm.selectItem(20L)
        vm.acceptInterestAndCreateMatch()
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("diizinkan untuk barter"))

        discoveryRepo.respondInterestResult = RespondInterestResult.TargetItemNotFound()
        vm.selectItem(20L)
        vm.acceptInterestAndCreateMatch()
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("tidak ditemukan"))
    }

    @Test
    fun formatChatThreadTimestamp_handlesEmptySameDaySameYearAndPriorYear() {
        val tz = TimeZone.getTimeZone("Asia/Jakarta")
        val locale = Locale.US

        assertEquals("", formatChatThreadTimestamp(null, timeZone = tz, locale = locale))
        assertEquals("", formatChatThreadTimestamp(0L, timeZone = tz, locale = locale))
        assertEquals("", formatChatThreadTimestamp(-100L, timeZone = tz, locale = locale))

        val now = Calendar.getInstance(tz, locale).apply {
            set(2026, Calendar.NOVEMBER, 23, 18, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val sameDay = Calendar.getInstance(tz, locale).apply {
            set(2026, Calendar.NOVEMBER, 23, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("14:30", formatChatThreadTimestamp(sameDay, nowMillis = now, locale = locale, timeZone = tz))

        val earlierThisYear = Calendar.getInstance(tz, locale).apply {
            set(2026, Calendar.OCTOBER, 5, 9, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("05 Oct", formatChatThreadTimestamp(earlierThisYear, nowMillis = now, locale = locale, timeZone = tz))

        val priorYear = Calendar.getInstance(tz, locale).apply {
            set(2025, Calendar.DECEMBER, 31, 23, 59, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("31/12/25", formatChatThreadTimestamp(priorYear, nowMillis = now, locale = locale, timeZone = tz))
    }
}
