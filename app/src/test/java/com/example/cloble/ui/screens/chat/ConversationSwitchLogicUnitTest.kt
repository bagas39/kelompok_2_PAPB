package com.example.cloble.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.DiscoverCandidate
import com.example.cloble.domain.model.DiscoverFilter
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
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.result.CancelInterestResult
import com.example.cloble.domain.result.CancelMatchResult
import com.example.cloble.domain.result.ChangePasswordResult
import com.example.cloble.domain.result.CompleteMatchResult
import com.example.cloble.domain.result.DeclineInterestResult
import com.example.cloble.domain.result.RequestPasswordResetOtpResult
import com.example.cloble.domain.result.ResetPasswordResult
import com.example.cloble.domain.result.RespondInterestResult
import com.example.cloble.domain.result.ReviewSubmitResult
import com.example.cloble.domain.result.SendInterestResult
import com.example.cloble.domain.result.SendMessageResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationSwitchLogicUnitTest {

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    private fun sampleItem(id: Long, ownerId: Long, name: String) = ClothingItem(
        id = id,
        ownerId = ownerId,
        name = name,
        description = "Bagus",
        category = "Atasan",
        size = "M",
        gender = "Uniseks",
        color = "Hitam",
        condition = "Baik",
        mainPhotoUri = null,
        detailPhotoUri = null,
        extraPhotoUri = null,
        isBarterAllowed = false,
        status = ItemStatus.AVAILABLE,
        createdAt = 1000L,
        updatedAt = 2000L
    )

    private fun sampleDetail(matchId: Long, status: MatchStatus) = VerifiedMatchDetail(
        matchId = matchId,
        status = status,
        meetingLocation = null,
        createdAt = 1000L,
        updatedAt = 2000L,
        myUserId = 1L,
        partnerUserId = 2L,
        partnerName = "Nadhif",
        partnerCity = "Surabaya",
        partnerAvatarUri = null,
        partnerAverageRating = 5.0,
        partnerReviewCount = 1,
        myItem = sampleItem(10L, 1L, "Pixel"),
        partnerItem = sampleItem(20L, 2L, "Feast")
    )

    private class FakeAuthRepo : AuthRepository {
        override fun observeLoggedInUserId(): Flow<Long?> = flowOf(1L)
        override fun observeCurrentUser(): Flow<UserProfile?> = flowOf(null)
        override suspend fun login(email: String, rawPassword: String) = AuthResult.InvalidCredentials
        override suspend fun requestPasswordResetOtp(email: String) = RequestPasswordResetOtpResult.Success
        override suspend fun verifyOtpAndResetPassword(email: String, otpCode: String, newRawPassword: String) =
            ResetPasswordResult.Success
        override suspend fun changePassword(currentRawPassword: String, newRawPassword: String) =
            ChangePasswordResult.Success
        override suspend fun register(
            name: String,
            email: String,
            rawPassword: String,
            city: String,
            latitude: Double?,
            longitude: Double?
        ) = AuthResult.InvalidCredentials
        override suspend fun logout() {}
    }

    private class FakeChatRepo : ChatRepository {
        var sendGate: CompletableDeferred<Unit>? = null
        override fun observeChatThreads(userId: Long): Flow<List<ChatThreadSummary>> = flowOf(emptyList())
        override fun observePartnerMatches(userId: Long, partnerUserId: Long): Flow<List<ChatThreadSummary>> =
            flowOf(emptyList())
        override fun observeMessages(matchId: Long, viewerUserId: Long): Flow<List<ChatMessage>> = flowOf(emptyList())
        override suspend fun sendMessage(matchId: Long, senderId: Long, text: String): SendMessageResult {
            sendGate?.await()
            return SendMessageResult.Success(1L)
        }
        override suspend fun markMessagesRead(matchId: Long, viewerUserId: Long) {}
    }

    private class FakeDiscoveryRepo(
        private val detailByMatch: Map<Long, Flow<VerifiedMatchDetail?>>
    ) : DiscoveryMatchRepository {
        override fun observeDiscoverCandidates(viewerUserId: Long, filter: DiscoverFilter): Flow<List<DiscoverCandidate>> =
            flowOf(emptyList())
        override fun observeMatchDetail(matchId: Long, viewerUserId: Long): Flow<VerifiedMatchDetail?> =
            detailByMatch[matchId] ?: flowOf(null)
        override fun observeNotifications(userId: Long): Flow<List<NotificationFeedItem>> = flowOf(emptyList())
        override fun observeUnreadNotificationCount(userId: Long): Flow<Int> = flowOf(0)
        override fun observeOutgoingInterests(userId: Long): Flow<List<OutgoingInterestSummary>> = flowOf(emptyList())
        override fun observePassedClothing(userId: Long): Flow<List<PassedClothingSummary>> = flowOf(emptyList())
        override suspend fun passCandidate(userId: Long, targetItemId: Long) = true
        override suspend fun sendInterest(senderId: Long, targetItemId: Long) = SendInterestResult.Success(1L)
        override suspend fun cancelOutgoingInterest(userId: Long, interestId: Long) = CancelInterestResult.Success
        override suspend fun convertPassToInterest(userId: Long, targetItemId: Long) = SendInterestResult.Success(1L)
        override suspend fun restorePassToDiscover(userId: Long, targetItemId: Long) = true
        override suspend fun respondToInterestAndCreateMatch(
            actorUserId: Long,
            interestId: Long,
            chosenSenderItemId: Long
        ) = RespondInterestResult.Success(1L)
        override suspend fun declineIncomingInterest(actorUserId: Long, interestId: Long) = DeclineInterestResult.Success
        override suspend fun cancelMatch(matchId: Long, actorUserId: Long) = CancelMatchResult.Success
        override suspend fun completeMatchExchange(matchId: Long, actorUserId: Long, meetingLocation: String?) =
            CompleteMatchResult.Success
        override suspend fun markNotificationsRead(userId: Long) {}
        override suspend fun markNotificationReadById(notificationId: Long) {}
    }

    private class FakeReviewRepo(
        private val reviewedByMatch: Map<Long, Flow<Boolean>>
    ) : ProfileReviewRepository {
        override fun observeUserProfile(userId: Long, viewerUserId: Long?): Flow<UserProfile?> = flowOf(null)
        override fun observeUserReviews(userId: Long): Flow<List<UserReview>> = flowOf(emptyList())
        override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> =
            reviewedByMatch[matchId] ?: flowOf(false)
        override suspend fun updatePreferences(userId: Long, prefs: UserPreferences) = true
        override suspend fun updateBio(userId: Long, bio: String) {}
        override suspend fun updateProfile(userId: Long, update: UserProfileUpdate) = true
        override suspend fun persistAvatarPhoto(sourceUriString: String): String? = null
        override suspend fun submitReview(matchId: Long, reviewerId: Long, rating: Int, comment: String) =
            ReviewSubmitResult.Success(1L)
    }

    @Test
    fun selectMatch_clearsDraftMessageAndIsBlockedWhileSendingMessage() = runTest {
        val sendGate = CompletableDeferred<Unit>()
        val chatRepo = FakeChatRepo().apply { this.sendGate = sendGate }
        val discoveryRepo = FakeDiscoveryRepo(
            mapOf(
                30L to flowOf(sampleDetail(30L, MatchStatus.ACTIVE)),
                12L to flowOf(sampleDetail(12L, MatchStatus.COMPLETED))
            )
        )
        val vm = ConversationViewModel(
            savedStateHandle = SavedStateHandle(mapOf("matchId" to 30L)),
            authRepository = FakeAuthRepo(),
            discoveryMatchRepository = discoveryRepo,
            chatRepository = chatRepo,
            profileReviewRepository = FakeReviewRepo(mapOf(30L to flowOf(false), 12L to flowOf(true)))
        )

        vm.onDraftChanged("draft untuk match 30")
        assertEquals("draft untuk match 30", vm.uiState.value.draftMessage)

        vm.selectMatch(12L)
        assertEquals(12L, vm.uiState.value.activeMatchId)
        assertEquals("", vm.uiState.value.draftMessage)

        vm.selectMatch(30L)
        vm.onDraftChanged("sedang dikirim")
        vm.sendMessage()
        assertTrue(vm.uiState.value.isSendingMessage)

        vm.selectMatch(12L)
        assertEquals(30L, vm.uiState.value.activeMatchId)

        sendGate.complete(Unit)
        assertFalse(vm.uiState.value.isSendingMessage)
        vm.selectMatch(12L)
        assertEquals(12L, vm.uiState.value.activeMatchId)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun selectMatch_completedAndReviewedMatch_waitsForReviewStatusWithoutFlashingRateButton() = runTest {
        val pendingReviewFlow = MutableSharedFlow<Boolean>(replay = 0)
        val detailFlow12 = flow { emit(sampleDetail(12L, MatchStatus.COMPLETED)) }
        val discoveryRepo = FakeDiscoveryRepo(
            mapOf(
                30L to flowOf(sampleDetail(30L, MatchStatus.ACTIVE)),
                12L to detailFlow12
            )
        )
        val reviewRepo = FakeReviewRepo(
            mapOf(
                30L to flowOf(false),
                12L to pendingReviewFlow
            )
        )
        val vm = ConversationViewModel(
            savedStateHandle = SavedStateHandle(mapOf("matchId" to 30L)),
            authRepository = FakeAuthRepo(),
            discoveryMatchRepository = discoveryRepo,
            chatRepository = FakeChatRepo(),
            profileReviewRepository = reviewRepo
        )

        vm.selectMatch(12L)

        assertEquals(MatchStatus.COMPLETED, vm.uiState.value.matchDetail?.status)
        assertFalse(vm.uiState.value.isReviewStatusLoaded)
        assertFalse(vm.uiState.value.canRatePartner)

        pendingReviewFlow.emit(true)

        assertTrue(vm.uiState.value.isReviewStatusLoaded)
        assertTrue(vm.uiState.value.hasReviewed)
        assertFalse(vm.uiState.value.canRatePartner)
    }
}
