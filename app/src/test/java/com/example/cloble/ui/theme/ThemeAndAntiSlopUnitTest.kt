package com.example.cloble.ui.theme

import androidx.lifecycle.SavedStateHandle
import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.DiscoverCandidate
import com.example.cloble.domain.model.DiscoverFilter
import com.example.cloble.domain.model.NotificationFeedItem
import com.example.cloble.domain.model.OutgoingInterestSummary
import com.example.cloble.domain.model.PassedClothingSummary
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.model.VerifiedMatchDetail
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.result.CancelInterestResult
import com.example.cloble.domain.result.CancelMatchResult
import com.example.cloble.domain.result.ChangePasswordResult
import com.example.cloble.domain.result.CompleteMatchResult
import com.example.cloble.domain.result.DeclineInterestResult
import com.example.cloble.domain.result.RequestPasswordResetOtpResult
import com.example.cloble.domain.result.ResetPasswordResult
import com.example.cloble.domain.result.RespondInterestResult
import com.example.cloble.domain.result.SendInterestResult
import com.example.cloble.domain.result.SendMessageResult
import com.example.cloble.ui.components.computeInitials
import com.example.cloble.ui.screens.chat.ChatListViewModel
import com.example.cloble.ui.screens.discover.DiscoverViewModel
import com.example.cloble.ui.screens.matched.MatchedViewModel
import com.example.cloble.ui.screens.notifications.NotificationsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeAndAntiSlopUnitTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun appThemeMode_fromStorageKey_parsesKnownAndUnknownSafely() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorageKey(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorageKey(""))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorageKey("unknown"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorageKey("system"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStorageKey("LIGHT"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStorageKey("dark"))
    }

    @Test
    fun computeInitials_returnsNullWhenNameBlankAndInitialsWhenPresent() {
        assertNull(computeInitials(""))
        assertNull(computeInitials("   "))
        assertEquals("BA", computeInitials("Bagas"))
        assertEquals("BP", computeInitials("Bagas Pratama"))
    }

    @Test
    fun chatListViewModel_surfacesErrorAndRecoversOnRetry() = runTest {
        var shouldThrow = true
        val authRepo = FakeSimpleAuthRepository(loggedInUserId = 1L)
        val chatRepo = object : FakeSimpleChatRepository() {
            override fun observeChatThreads(userId: Long): Flow<List<ChatThreadSummary>> = flow {
                if (shouldThrow) throw IllegalStateException("network down")
                emit(emptyList())
            }
        }
        val matchRepo = FakeSimpleMatchRepository()

        val vm = ChatListViewModel(authRepo, chatRepo, matchRepo)
        assertNotNull(vm.uiState.value.errorMessage)

        shouldThrow = false
        vm.retry()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun notificationsViewModel_surfacesErrorAndRecoversOnRetry() = runTest {
        var shouldThrow = true
        val authRepo = FakeSimpleAuthRepository(loggedInUserId = 1L)
        val matchRepo = object : FakeSimpleMatchRepository() {
            override fun observeNotifications(userId: Long): Flow<List<NotificationFeedItem>> = flow {
                if (shouldThrow) throw IllegalStateException("timeout")
                emit(emptyList())
            }
        }

        val vm = NotificationsViewModel(authRepo, matchRepo)
        assertNotNull(vm.uiState.value.errorMessage)

        shouldThrow = false
        vm.retry()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun discoverViewModel_surfacesLoadErrorAndRecoversOnRetry() = runTest {
        var shouldThrow = true
        val authRepo = FakeSimpleAuthRepository(loggedInUserId = 1L)
        val matchRepo = object : FakeSimpleMatchRepository() {
            override fun observeDiscoverCandidates(
                viewerUserId: Long,
                filter: DiscoverFilter
            ): Flow<List<DiscoverCandidate>> = flow {
                if (shouldThrow) throw IllegalStateException("rpc error")
                emit(emptyList())
            }
        }

        val vm = DiscoverViewModel(authRepo, matchRepo)
        assertNotNull(vm.uiState.value.loadErrorMessage)

        shouldThrow = false
        vm.retryLoadCandidates()
        assertNull(vm.uiState.value.loadErrorMessage)
    }

    @Test
    fun matchedViewModel_surfacesErrorAndRecoversOnRetry() = runTest {
        var shouldThrow = true
        val authRepo = FakeSimpleAuthRepository(loggedInUserId = 1L)
        val matchRepo = object : FakeSimpleMatchRepository() {
            override fun observeMatchDetail(matchId: Long, viewerUserId: Long): Flow<VerifiedMatchDetail?> = flow {
                if (shouldThrow) throw IllegalStateException("match fetch error")
                emit(null)
            }
        }

        val vm = MatchedViewModel(SavedStateHandle(mapOf("matchId" to 42L)), authRepo, matchRepo)
        assertNotNull(vm.uiState.value.errorMessage)

        shouldThrow = false
        vm.retry()
        assertNull(vm.uiState.value.errorMessage)
    }

    private class FakeSimpleAuthRepository(private val loggedInUserId: Long?) : AuthRepository {
        override fun observeLoggedInUserId(): Flow<Long?> = flowOf(loggedInUserId)
        override fun observeCurrentUser(): Flow<UserProfile?> = flowOf(null)
        override suspend fun login(email: String, rawPassword: String): AuthResult = AuthResult.InvalidCredentials
        override suspend fun requestPasswordResetOtp(email: String): RequestPasswordResetOtpResult =
            RequestPasswordResetOtpResult.Success
        override suspend fun verifyOtpAndResetPassword(
            email: String,
            otpCode: String,
            newRawPassword: String
        ): ResetPasswordResult = ResetPasswordResult.Success
        override suspend fun changePassword(currentRawPassword: String, newRawPassword: String): ChangePasswordResult =
            ChangePasswordResult.Success
        override suspend fun register(
            name: String,
            email: String,
            rawPassword: String,
            city: String,
            latitude: Double?,
            longitude: Double?
        ): AuthResult = AuthResult.Success(1L)
        override suspend fun logout() {}
    }

    private open class FakeSimpleChatRepository : ChatRepository {
        override fun observeChatThreads(userId: Long): Flow<List<ChatThreadSummary>> = flowOf(emptyList())
        override fun observePartnerMatches(userId: Long, partnerUserId: Long): Flow<List<ChatThreadSummary>> =
            flowOf(emptyList())
        override fun observeMessages(matchId: Long, viewerUserId: Long): Flow<List<ChatMessage>> = flowOf(emptyList())
        override suspend fun sendMessage(matchId: Long, senderId: Long, text: String): SendMessageResult =
            SendMessageResult.Success(1L)
        override suspend fun markMessagesRead(matchId: Long, viewerUserId: Long) {}
    }

    private open class FakeSimpleMatchRepository : DiscoveryMatchRepository {
        override fun observeDiscoverCandidates(viewerUserId: Long, filter: DiscoverFilter): Flow<List<DiscoverCandidate>> =
            flowOf(emptyList())
        override fun observeMatchDetail(matchId: Long, viewerUserId: Long): Flow<VerifiedMatchDetail?> = flowOf(null)
        override fun observeNotifications(userId: Long): Flow<List<NotificationFeedItem>> = flowOf(emptyList())
        override fun observeUnreadNotificationCount(userId: Long): Flow<Int> = flowOf(0)
        override fun observeOutgoingInterests(userId: Long): Flow<List<OutgoingInterestSummary>> = flowOf(emptyList())
        override fun observePassedClothing(userId: Long): Flow<List<PassedClothingSummary>> = flowOf(emptyList())
        override suspend fun passCandidate(userId: Long, targetItemId: Long): Boolean = true
        override suspend fun sendInterest(senderId: Long, targetItemId: Long): SendInterestResult =
            SendInterestResult.Success(1L)
        override suspend fun cancelOutgoingInterest(userId: Long, interestId: Long): CancelInterestResult =
            CancelInterestResult.Success
        override suspend fun convertPassToInterest(userId: Long, targetItemId: Long): SendInterestResult =
            SendInterestResult.Success(1L)
        override suspend fun restorePassToDiscover(userId: Long, targetItemId: Long): Boolean = true
        override suspend fun respondToInterestAndCreateMatch(
            actorUserId: Long,
            interestId: Long,
            chosenSenderItemId: Long
        ): RespondInterestResult = RespondInterestResult.Success(1L)
        override suspend fun declineIncomingInterest(actorUserId: Long, interestId: Long): DeclineInterestResult =
            DeclineInterestResult.Success
        override suspend fun cancelMatch(matchId: Long, actorUserId: Long): CancelMatchResult =
            CancelMatchResult.Success
        override suspend fun completeMatchExchange(
            matchId: Long,
            actorUserId: Long,
            meetingLocation: String?
        ): CompleteMatchResult = CompleteMatchResult.Success
        override suspend fun markNotificationsRead(userId: Long) {}
        override suspend fun markNotificationReadById(notificationId: Long) {}
    }
}
