package com.example.cloble.ui.screens.auth

import com.example.cloble.domain.model.ClothingDraft
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.DiscoverCandidate
import com.example.cloble.domain.model.DiscoverFilter
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
import com.example.cloble.domain.result.WardrobeMutationResult
import com.example.cloble.ui.screens.profile.MyProfileViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthProductionViewModelUnitTest {

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    private class FakeAuthRepository(
        var loginResult: AuthResult = AuthResult.InvalidCredentials,
        var registerResult: AuthResult = AuthResult.Success(10L)
    ) : AuthRepository {
        var registerCalled = false
        var changePasswordCalled = false
        var resetVerifyCalled = false
        var loginCalls = 0
        var resetOtpCalls = 0
        var registerCalls = 0
        var logoutCalls = 0
        var loginGate: CompletableDeferred<Unit>? = null
        var resetOtpGate: CompletableDeferred<Unit>? = null
        var registerGate: CompletableDeferred<Unit>? = null
        var logoutGate: CompletableDeferred<Unit>? = null

        override fun observeLoggedInUserId(): Flow<Long?> = MutableStateFlow(10L)
        override fun observeCurrentUser(): Flow<UserProfile?> = MutableStateFlow(null)

        override suspend fun login(email: String, rawPassword: String): AuthResult {
            loginCalls++
            loginGate?.await()
            return loginResult
        }

        override suspend fun requestPasswordResetOtp(email: String): RequestPasswordResetOtpResult {
            resetOtpCalls++
            resetOtpGate?.await()
            return RequestPasswordResetOtpResult.Success
        }

        override suspend fun verifyOtpAndResetPassword(
            email: String,
            otpCode: String,
            newRawPassword: String
        ): ResetPasswordResult {
            resetVerifyCalled = true
            return ResetPasswordResult.Success
        }

        override suspend fun changePassword(
            currentRawPassword: String,
            newRawPassword: String
        ): ChangePasswordResult {
            changePasswordCalled = true
            return ChangePasswordResult.Success
        }

        override suspend fun register(
            name: String,
            email: String,
            rawPassword: String,
            city: String,
            latitude: Double?,
            longitude: Double?
        ): AuthResult {
            registerCalled = true
            registerCalls++
            registerGate?.await()
            return registerResult
        }

        override suspend fun logout() {
            logoutCalls++
            logoutGate?.await()
        }
    }

    private class StubProfileReviewRepository : ProfileReviewRepository {
        override fun observeUserProfile(userId: Long, viewerUserId: Long?): Flow<UserProfile?> = flowOf(null)
        override fun observeUserReviews(userId: Long): Flow<List<UserReview>> = flowOf(emptyList())
        override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> = flowOf(false)
        override suspend fun updatePreferences(userId: Long, prefs: UserPreferences) = true
        override suspend fun updateBio(userId: Long, bio: String) {}
        override suspend fun updateProfile(userId: Long, update: UserProfileUpdate) = true
        override suspend fun persistAvatarPhoto(sourceUriString: String): String? = null
        override suspend fun submitReview(matchId: Long, reviewerId: Long, rating: Int, comment: String) =
            ReviewSubmitResult.MatchNotFound
    }

    private class StubWardrobeRepository : WardrobeRepository {
        override fun observeMyWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeUserPublicWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeClothingItem(itemId: Long): Flow<ClothingItem?> = flowOf(null)
        override suspend fun getClothingItem(itemId: Long): ClothingItem? = null
        override suspend fun isItemLocked(itemId: Long): Boolean = false
        override suspend fun addClothingItem(ownerId: Long, draft: ClothingDraft) = WardrobeMutationResult.ItemNotFound
        override suspend fun updateClothingItem(actorUserId: Long, itemId: Long, draft: ClothingDraft) =
            WardrobeMutationResult.ItemNotFound
        override suspend fun setBarterAllowed(actorUserId: Long, itemId: Long, allowed: Boolean) =
            WardrobeMutationResult.ItemNotFound
        override suspend fun softDeleteClothingItem(actorUserId: Long, itemId: Long) =
            WardrobeMutationResult.ItemNotFound
        override suspend fun persistLocalPhoto(sourceUriString: String): String? = null
        override suspend fun countEligibleBarterItems(ownerId: Long): Int = 0
    }

    private class StubDiscoveryMatchRepository : DiscoveryMatchRepository {
        override fun observeDiscoverCandidates(viewerUserId: Long, filter: DiscoverFilter): Flow<List<DiscoverCandidate>> =
            flowOf(emptyList())
        override fun observeMatchDetail(matchId: Long, viewerUserId: Long): Flow<VerifiedMatchDetail?> = flowOf(null)
        override fun observeNotifications(userId: Long): Flow<List<NotificationFeedItem>> = flowOf(emptyList())
        override fun observeUnreadNotificationCount(userId: Long): Flow<Int> = flowOf(0)
        override fun observeOutgoingInterests(userId: Long): Flow<List<OutgoingInterestSummary>> = flowOf(emptyList())
        override fun observePassedClothing(userId: Long): Flow<List<PassedClothingSummary>> = flowOf(emptyList())
        override suspend fun passCandidate(userId: Long, targetItemId: Long) = true
        override suspend fun sendInterest(senderId: Long, targetItemId: Long) = SendInterestResult.TargetItemNotFound
        override suspend fun cancelOutgoingInterest(userId: Long, interestId: Long) = CancelInterestResult.Success
        override suspend fun convertPassToInterest(userId: Long, targetItemId: Long) = SendInterestResult.TargetItemNotFound
        override suspend fun restorePassToDiscover(userId: Long, targetItemId: Long) = true
        override suspend fun respondToInterestAndCreateMatch(
            actorUserId: Long,
            interestId: Long,
            chosenSenderItemId: Long
        ) = RespondInterestResult.InterestAlreadyProcessedOrInvalid
        override suspend fun declineIncomingInterest(actorUserId: Long, interestId: Long) = DeclineInterestResult.Success
        override suspend fun cancelMatch(matchId: Long, actorUserId: Long) = CancelMatchResult.Success
        override suspend fun completeMatchExchange(
            matchId: Long,
            actorUserId: Long,
            meetingLocation: String?
        ) = CompleteMatchResult.Success
        override suspend fun markNotificationsRead(userId: Long) {}
        override suspend fun markNotificationReadById(notificationId: Long) {}
    }

    @Test
    fun loginViewModel_whenRateLimited_displaysCooldownMessage() = runTest {
        val repo = FakeAuthRepository(
            loginResult = AuthResult.RateLimited("Terlalu banyak percobaan login gagal. Coba lagi dalam 15 menit.")
        )
        val viewModel = LoginViewModel(repo)
        var succeeded = false

        viewModel.onEmailChanged("user@cloble.id")
        viewModel.onPasswordChanged("Secret123")
        viewModel.submitLogin { succeeded = true }

        assertFalse(succeeded)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(
            "Terlalu banyak percobaan login gagal. Coba lagi dalam 15 menit.",
            viewModel.uiState.value.errorMessage
        )
    }

    @Test
    fun loginViewModel_resetPassword_rejectsWeakPasswordBeforeRpc() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = LoginViewModel(repo)

        viewModel.onResetEmailChanged("user@cloble.id")
        viewModel.onResetOtpCodeChanged("123456")
        viewModel.onResetNewPasswordChanged("onlyletters")
        viewModel.onResetConfirmPasswordChanged("onlyletters")
        viewModel.submitResetPassword()

        assertFalse(repo.resetVerifyCalled)
        assertEquals("Password harus mengandung huruf dan angka", viewModel.uiState.value.resetErrorMessage)
    }

    @Test
    fun registerViewModel_requiresLocationSelectionBeforeSubmit() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = RegisterViewModel(repo)

        assertFalse(viewModel.uiState.value.hasSelectedLocation)

        viewModel.onNameChanged("Budi")
        viewModel.onEmailChanged("budi@cloble.id")
        viewModel.onPasswordChanged("Budi1234")
        viewModel.onConfirmPasswordChanged("Budi1234")
        viewModel.submitRegister {}

        assertFalse(repo.registerCalled)
        assertEquals(
            "Pilih lokasi domisili Anda di peta terlebih dahulu",
            viewModel.uiState.value.errorMessage
        )
    }

    @Test
    fun registerViewModel_validatesPasswordAndPropagatesDatabaseFailure() = runTest {
        val repo = FakeAuthRepository(
            registerResult = AuthResult.DatabaseError(
                DbFailure.UnexpectedDatabaseError("Terjadi gangguan koneksi ke server. Silakan coba lagi.")
            )
        )
        val viewModel = RegisterViewModel(repo)

        viewModel.onNameChanged("Budi")
        viewModel.onEmailChanged("budi@cloble.id")
        viewModel.onLocationSelected("Surabaya, Jawa Timur", -7.2575, 112.7521)
        viewModel.onPasswordChanged("1234567")
        viewModel.onConfirmPasswordChanged("1234567")
        viewModel.submitRegister {}

        assertFalse(repo.registerCalled)
        assertEquals("Password minimal 8 karakter", viewModel.uiState.value.errorMessage)

        viewModel.onPasswordChanged("Budi1234")
        viewModel.onConfirmPasswordChanged("Budi1234")
        viewModel.submitRegister {}

        assertTrue(repo.registerCalled)
        assertEquals(
            "Terjadi gangguan koneksi ke server. Silakan coba lagi.",
            viewModel.uiState.value.errorMessage
        )
    }

    @Test
    fun myProfileViewModel_changePassword_enforcesMinLengthAndMix() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = MyProfileViewModel(
            authRepository = repo,
            profileReviewRepository = StubProfileReviewRepository(),
            wardrobeRepository = StubWardrobeRepository(),
            discoveryMatchRepository = StubDiscoveryMatchRepository()
        )

        viewModel.openChangePasswordDialog()
        viewModel.onCurrentPasswordChanged("OldPass123")
        viewModel.onNewPasswordChanged("weak12")
        viewModel.onConfirmPasswordChanged("weak12")
        viewModel.submitChangePassword()

        assertFalse(repo.changePasswordCalled)
        assertEquals("Password minimal 8 karakter", viewModel.uiState.value.passwordErrorMessage)

        viewModel.onNewPasswordChanged("onlyletters")
        viewModel.onConfirmPasswordChanged("onlyletters")
        viewModel.submitChangePassword()

        assertFalse(repo.changePasswordCalled)
        assertEquals("Password harus mengandung huruf dan angka", viewModel.uiState.value.passwordErrorMessage)
    }

    @Test
    fun loginViewModel_guardsDoubleSubmitLoginAndDoubleRequestOtp() = runTest {
        val loginGate = CompletableDeferred<Unit>()
        val otpGate = CompletableDeferred<Unit>()
        val repo = FakeAuthRepository(loginResult = AuthResult.Success(10L)).apply {
            this.loginGate = loginGate
            this.resetOtpGate = otpGate
        }
        val viewModel = LoginViewModel(repo)

        viewModel.onEmailChanged("user@cloble.id")
        viewModel.onPasswordChanged("Password123")
        var loginCallbacks = 0
        viewModel.submitLogin { loginCallbacks++ }
        assertTrue(viewModel.uiState.value.isLoading)
        viewModel.submitLogin { loginCallbacks++ }
        assertEquals(1, repo.loginCalls)
        loginGate.complete(Unit)
        assertEquals(1, loginCallbacks)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.onResetEmailChanged("user@cloble.id")
        viewModel.requestResetOtp()
        assertTrue(viewModel.uiState.value.isResetSubmitting)
        viewModel.requestResetOtp()
        assertEquals(1, repo.resetOtpCalls)
        otpGate.complete(Unit)
        assertFalse(viewModel.uiState.value.isResetSubmitting)
    }

    @Test
    fun registerViewModel_guardsDoubleSubmitRegister() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeAuthRepository().apply { registerGate = gate }
        val viewModel = RegisterViewModel(repo)

        viewModel.onNameChanged("Budi")
        viewModel.onEmailChanged("budi@cloble.id")
        viewModel.onPasswordChanged("Budi1234")
        viewModel.onConfirmPasswordChanged("Budi1234")
        viewModel.onLocationSelected("Bandung", -6.9175, 107.6191)

        var callbacks = 0
        viewModel.submitRegister { callbacks++ }
        assertTrue(viewModel.uiState.value.isSubmitting)
        viewModel.submitRegister { callbacks++ }
        assertEquals(1, repo.registerCalls)

        gate.complete(Unit)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(1, callbacks)
    }

    @Test
    fun myProfileViewModel_guardsDoubleLogout() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeAuthRepository().apply { logoutGate = gate }
        val viewModel = MyProfileViewModel(
            authRepository = repo,
            profileReviewRepository = StubProfileReviewRepository(),
            wardrobeRepository = StubWardrobeRepository(),
            discoveryMatchRepository = StubDiscoveryMatchRepository()
        )

        var callbacks = 0
        viewModel.logout { callbacks++ }
        viewModel.logout { callbacks++ }
        assertEquals(1, repo.logoutCalls)

        gate.complete(Unit)
        assertEquals(1, callbacks)
    }
}
