package com.example.cloble.ui.screens

import androidx.compose.runtime.saveable.SaverScope
import androidx.lifecycle.SavedStateHandle
import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ClothingDraft
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.DiscoverCandidate
import com.example.cloble.domain.model.DiscoverFilter
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.NotificationFeedItem
import com.example.cloble.domain.model.NotificationType
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
import com.example.cloble.domain.result.DeclineInterestResult
import com.example.cloble.domain.result.RequestPasswordResetOtpResult
import com.example.cloble.domain.result.ResetPasswordResult
import com.example.cloble.domain.result.RespondInterestResult
import com.example.cloble.domain.result.ReviewSubmitResult
import com.example.cloble.domain.result.SendInterestResult
import com.example.cloble.domain.result.WardrobeMutationResult
import com.example.cloble.ui.components.LabeledPhoto
import com.example.cloble.ui.components.LabeledPhotoListSaver
import com.example.cloble.ui.components.NullableLabeledPhotoSaver
import com.example.cloble.ui.screens.notifications.NotificationCardAction
import com.example.cloble.ui.screens.notifications.resolveNotificationCardAction
import com.example.cloble.ui.screens.preferences.PreferencesViewModel
import com.example.cloble.ui.screens.profile.EditProfileViewModel
import com.example.cloble.ui.screens.profile.computeEditInitials
import com.example.cloble.ui.screens.wardrobe.AddEditClothingViewModel
import com.example.cloble.ui.screens.wardrobe.WardrobeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuditBugFixesUnitTest {

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    private fun sampleProfile(): UserProfile = UserProfile(
        id = 10L,
        name = "Bagas",
        email = "bagas@example.com",
        bio = "Bio",
        avatarUri = null,
        city = "Kota Malang",
        latitude = -7.9666,
        longitude = 112.6326,
        preferredSize = "M",
        preferredGender = "Uniseks",
        preferredRadiusKm = 10,
        createdAt = 1700000000000L
    )

    private class FakeAuthRepo(private val profile: UserProfile) : AuthRepository {
        override fun observeLoggedInUserId(): Flow<Long?> = flowOf(profile.id)
        override fun observeCurrentUser(): Flow<UserProfile?> = flowOf(profile)
        override suspend fun login(email: String, rawPassword: String): AuthResult = AuthResult.Success(profile.id)
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
        ): AuthResult = AuthResult.Success(profile.id)
        override suspend fun logout() {}
    }

    private class CountingWardrobeRepo : WardrobeRepository {
        var observeMyWardrobeCalls = 0

        override fun observeMyWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> {
            observeMyWardrobeCalls++
            return flowOf(emptyList())
        }

        override fun observeUserPublicWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
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
        override suspend fun countEligibleBarterItems(ownerId: Long): Int = 0
    }

    private class FakeProfileRepo : ProfileReviewRepository {
        override fun observeUserProfile(userId: Long, viewerUserId: Long?): Flow<UserProfile?> = flowOf(null)
        override fun observeUserReviews(userId: Long): Flow<List<UserReview>> = flowOf(emptyList())
        override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> = flowOf(false)
        override suspend fun updatePreferences(userId: Long, prefs: UserPreferences): Boolean = true
        override suspend fun updateBio(userId: Long, bio: String) {}
        override suspend fun updateProfile(userId: Long, update: UserProfileUpdate): Boolean = true
        override suspend fun persistAvatarPhoto(sourceUriString: String): String? = null
        override suspend fun submitReview(matchId: Long, reviewerId: Long, rating: Int, comment: String): ReviewSubmitResult =
            ReviewSubmitResult.Success(1L)
    }

    private class StubDiscoveryMatchRepo : DiscoveryMatchRepository {
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
        override suspend fun cancelMatch(matchId: Long, actorUserId: Long): CancelMatchResult = CancelMatchResult.Success
        override suspend fun completeMatchExchange(matchId: Long, actorUserId: Long, meetingLocation: String?): CompleteMatchResult =
            CompleteMatchResult.Success
        override suspend fun markNotificationsRead(userId: Long) {}
        override suspend fun markNotificationReadById(notificationId: Long) {}
    }

    @Test
    fun addEditClothingViewModel_incrementsErrorScrollTriggerOnRepeatedSameValidationError() = runTest {
        val vm = AddEditClothingViewModel(
            savedStateHandle = SavedStateHandle(),
            authRepository = FakeAuthRepo(sampleProfile()),
            wardrobeRepository = CountingWardrobeRepo()
        )
        assertEquals(0, vm.uiState.value.errorScrollTrigger)
        assertNull(vm.uiState.value.errorMessage)

        vm.save {}
        assertEquals("Nama pakaian wajib diisi", vm.uiState.value.errorMessage)
        assertEquals(1, vm.uiState.value.errorScrollTrigger)

        vm.save {}
        assertEquals("Nama pakaian wajib diisi", vm.uiState.value.errorMessage)
        assertEquals(2, vm.uiState.value.errorScrollTrigger)

        vm.onNameChanged("Kemeja Putih")
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(2, vm.uiState.value.errorScrollTrigger)
    }

    @Test
    fun editProfileViewModel_incrementsErrorScrollTriggerOnRepeatedSameValidationError() = runTest {
        val vm = EditProfileViewModel(FakeAuthRepo(sampleProfile()), FakeProfileRepo())
        assertEquals(0, vm.uiState.value.errorScrollTrigger)

        vm.onNameChanged("   ")
        vm.saveProfile {}
        assertEquals("Nama lengkap tidak boleh kosong.", vm.uiState.value.errorMessage)
        assertEquals(1, vm.uiState.value.errorScrollTrigger)

        vm.saveProfile {}
        assertEquals("Nama lengkap tidak boleh kosong.", vm.uiState.value.errorMessage)
        assertEquals(2, vm.uiState.value.errorScrollTrigger)

        vm.onNameChanged("Bagas")
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(2, vm.uiState.value.errorScrollTrigger)
    }

    @Test
    fun preferencesViewModel_incrementsErrorScrollTriggerOnRepeatedSameValidationError() = runTest {
        val vm = PreferencesViewModel(
            savedStateHandle = SavedStateHandle(mapOf("isOnboarding" to false)),
            authRepository = FakeAuthRepo(sampleProfile()),
            profileReviewRepository = FakeProfileRepo()
        )
        assertEquals(0, vm.uiState.value.errorScrollTrigger)

        vm.onCustomRadiusModeSelected()
        vm.onCustomRadiusTextChanged("")
        vm.savePreferences {}
        assertEquals("Radius pencarian harus diisi lebih dari 0 km.", vm.uiState.value.errorMessage)
        assertEquals(1, vm.uiState.value.errorScrollTrigger)

        vm.savePreferences {}
        assertEquals("Radius pencarian harus diisi lebih dari 0 km.", vm.uiState.value.errorMessage)
        assertEquals(2, vm.uiState.value.errorScrollTrigger)

        vm.onCustomRadiusTextChanged("20")
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(2, vm.uiState.value.errorScrollTrigger)
    }

    @Test
    fun computeEditInitials_returnsNullForEmptyOrBlankAndInitialsForValidNames() {
        assertNull(computeEditInitials(""))
        assertNull(computeEditInitials("    "))
        assertEquals("BA", computeEditInitials("Bagas"))
        assertEquals("A", computeEditInitials("A"))
        assertEquals("BA", computeEditInitials("Bagas Adi"))
        assertEquals("AB", computeEditInitials("  alice   bob charlie "))
    }

    @Test
    fun labeledPhotoSavers_roundTripValues() {
        val scope = SaverScope { true }

        val emptySaved = with(LabeledPhotoListSaver) { scope.save(emptyList()) }
        val restoredEmpty = emptySaved?.let { LabeledPhotoListSaver.restore(it) } ?: emptyList()
        assertEquals(emptyList<LabeledPhoto>(), restoredEmpty)

        val singleItemList = listOf(LabeledPhoto("https://example.com/1.jpg", ""))
        val savedSingleList = with(LabeledPhotoListSaver) { scope.save(singleItemList) }!!
        assertEquals(singleItemList, LabeledPhotoListSaver.restore(savedSingleList))

        val originalList = listOf(
            LabeledPhoto("https://example.com/1.jpg", "Foto Utama"),
            LabeledPhoto("https://example.com/2.jpg", "Detail")
        )
        val savedList = with(LabeledPhotoListSaver) { scope.save(originalList) }!!
        assertEquals(originalList, LabeledPhotoListSaver.restore(savedList))

        val nullSaved = with(NullableLabeledPhotoSaver) { scope.save(null) }
        assertNull(nullSaved?.let { NullableLabeledPhotoSaver.restore(it) })

        val single = LabeledPhoto("https://example.com/3.jpg", "Kemeja")
        val singleSaved = with(NullableLabeledPhotoSaver) { scope.save(single) }!!
        assertEquals(single, NullableLabeledPhotoSaver.restore(singleSaved))
    }

    @Test
    fun wardrobeViewModel_onScreenResumed_clearsErrorSkipsFirstAndRefreshesLater() = runTest {
        val wardrobeRepo = CountingWardrobeRepo()
        val vm = WardrobeViewModel(
            authRepository = FakeAuthRepo(sampleProfile()),
            wardrobeRepository = wardrobeRepo,
            discoveryMatchRepository = StubDiscoveryMatchRepo()
        )
        assertEquals(1, wardrobeRepo.observeMyWardrobeCalls)

        vm.showLockedItemWarning("Kemeja Denim", isInActiveMatch = true)
        assertEquals(true, vm.uiState.value.errorMessage != null)

        vm.onScreenResumed()
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(1, wardrobeRepo.observeMyWardrobeCalls)

        vm.showLockedItemWarning("Jaket", isInActiveMatch = false)
        vm.onScreenResumed()
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(2, wardrobeRepo.observeMyWardrobeCalls)
    }

    @Test
    fun resolveNotificationCardAction_mapsStatesWithoutOpeningCancelledChats() {
        val base = NotificationFeedItem(
            id = 1L,
            userId = 10L,
            type = NotificationType.MATCH_VERIFIED,
            title = "Match",
            body = "Body",
            relatedInterestId = 2L,
            relatedMatchId = 3L,
            isRead = false,
            createdAt = 1000L
        )

        assertEquals(
            NotificationCardAction.OPEN_CONVERSATION,
            resolveNotificationCardAction(base.copy(matchStatus = MatchStatus.ACTIVE))
        )
        assertEquals(
            NotificationCardAction.NONE,
            resolveNotificationCardAction(base.copy(matchStatus = MatchStatus.CANCELLED))
        )
        assertEquals(
            NotificationCardAction.RATE_USER,
            resolveNotificationCardAction(base.copy(matchStatus = MatchStatus.COMPLETED, hasReviewedMatch = false))
        )
        assertEquals(
            NotificationCardAction.NONE,
            resolveNotificationCardAction(base.copy(matchStatus = MatchStatus.COMPLETED, hasReviewedMatch = true))
        )
        assertEquals(
            NotificationCardAction.NONE,
            resolveNotificationCardAction(base.copy(relatedMatchId = null, matchStatus = MatchStatus.ACTIVE))
        )

        assertEquals(
            NotificationCardAction.REPLY_INTEREST,
            resolveNotificationCardAction(
                base.copy(
                    type = NotificationType.INCOMING_INTEREST,
                    canReplyToInterest = true,
                    counterpartUserId = 99L,
                    relatedInterestId = 50L
                )
            )
        )
        assertEquals(
            NotificationCardAction.NONE,
            resolveNotificationCardAction(
                base.copy(
                    type = NotificationType.INCOMING_INTEREST,
                    canReplyToInterest = false,
                    counterpartUserId = 99L,
                    relatedInterestId = 50L
                )
            )
        )

        assertEquals(
            NotificationCardAction.RATE_USER,
            resolveNotificationCardAction(
                base.copy(
                    type = NotificationType.EXCHANGE_COMPLETED,
                    hasReviewedMatch = false
                )
            )
        )
        assertEquals(
            NotificationCardAction.NONE,
            resolveNotificationCardAction(
                base.copy(
                    type = NotificationType.EXCHANGE_COMPLETED,
                    hasReviewedMatch = true
                )
            )
        )
    }
}
