package com.example.cloble.ui.screens

import androidx.lifecycle.SavedStateHandle
import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.ClothingDraft
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
import com.example.cloble.domain.result.DeclineInterestResult
import com.example.cloble.domain.result.RequestPasswordResetOtpResult
import com.example.cloble.domain.result.ResetPasswordResult
import com.example.cloble.domain.result.RespondInterestResult
import com.example.cloble.domain.result.ReviewSubmitResult
import com.example.cloble.domain.result.SendInterestResult
import com.example.cloble.domain.result.SendMessageResult
import com.example.cloble.domain.result.WardrobeMutationResult
import com.example.cloble.ui.screens.chat.ConversationViewModel
import com.example.cloble.ui.screens.preferences.PreferencesViewModel
import com.example.cloble.ui.screens.profile.EditProfileViewModel
import com.example.cloble.ui.screens.review.RateUserViewModel
import com.example.cloble.ui.screens.wardrobe.AddEditClothingViewModel
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
class ViewModelHardeningUnitTest {

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
        city = "Malang",
        latitude = -7.9666,
        longitude = 112.6326,
        preferredSize = "M",
        preferredGender = "Uniseks",
        preferredRadiusKm = 10,
        createdAt = 1700000000000L
    )

    private fun sampleClothingItem(id: Long = 55L): ClothingItem = ClothingItem(
        id = id,
        ownerId = 10L,
        name = "Jaket Denim",
        description = "Masih bagus",
        category = "Luaran",
        size = "M",
        gender = "Uniseks",
        color = "Biru",
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
        myItem = sampleClothingItem(1L),
        partnerItem = sampleClothingItem(2L)
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

    private class FakeWardrobeRepo(private val existing: ClothingItem?) : WardrobeRepository {
        var addCalls = 0
        var deleteCalls = 0
        var addGate: CompletableDeferred<Unit>? = null
        var deleteGate: CompletableDeferred<Unit>? = null

        override fun observeMyWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeUserPublicWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeClothingItem(itemId: Long): Flow<ClothingItem?> = flowOf(existing)
        override suspend fun getClothingItem(itemId: Long): ClothingItem? = existing
        override suspend fun isItemLocked(itemId: Long): Boolean = false

        override suspend fun addClothingItem(ownerId: Long, draft: ClothingDraft): WardrobeMutationResult {
            addCalls++
            addGate?.await()
            return WardrobeMutationResult.Success(101L)
        }

        override suspend fun updateClothingItem(actorUserId: Long, itemId: Long, draft: ClothingDraft): WardrobeMutationResult =
            WardrobeMutationResult.Success(itemId)

        override suspend fun setBarterAllowed(actorUserId: Long, itemId: Long, allowed: Boolean): WardrobeMutationResult =
            WardrobeMutationResult.Success(itemId)

        override suspend fun softDeleteClothingItem(actorUserId: Long, itemId: Long): WardrobeMutationResult {
            deleteCalls++
            deleteGate?.await()
            return WardrobeMutationResult.Success(itemId)
        }

        override suspend fun persistLocalPhoto(sourceUriString: String): String? = null
        override suspend fun countEligibleBarterItems(ownerId: Long): Int = 1
    }

    private class FakeProfileReviewRepo : ProfileReviewRepository {
        var avatarCalls = 0
        var updateProfileCalls = 0
        var updatePrefsCalls = 0
        var submitReviewCalls = 0
        var avatarGate: CompletableDeferred<Unit>? = null
        var updateProfileGate: CompletableDeferred<Unit>? = null
        var updatePrefsGate: CompletableDeferred<Unit>? = null
        var submitReviewGate: CompletableDeferred<Unit>? = null

        override fun observeUserProfile(userId: Long, viewerUserId: Long?): Flow<UserProfile?> = flowOf(null)
        override fun observeUserReviews(userId: Long): Flow<List<UserReview>> = flowOf(emptyList())
        override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> = flowOf(false)

        override suspend fun updatePreferences(userId: Long, prefs: UserPreferences): Boolean {
            updatePrefsCalls++
            updatePrefsGate?.await()
            return true
        }

        override suspend fun updateBio(userId: Long, bio: String) {}

        override suspend fun updateProfile(userId: Long, update: UserProfileUpdate): Boolean {
            updateProfileCalls++
            updateProfileGate?.await()
            return true
        }

        override suspend fun persistAvatarPhoto(sourceUriString: String): String? {
            avatarCalls++
            avatarGate?.await()
            return null
        }

        override suspend fun submitReview(matchId: Long, reviewerId: Long, rating: Int, comment: String): ReviewSubmitResult {
            submitReviewCalls++
            submitReviewGate?.await()
            return ReviewSubmitResult.Success(7L)
        }
    }

    private class FakeDiscoveryMatchRepo(private val detail: VerifiedMatchDetail) : DiscoveryMatchRepository {
        override fun observeDiscoverCandidates(viewerUserId: Long, filter: DiscoverFilter): Flow<List<DiscoverCandidate>> =
            flowOf(emptyList())
        override fun observeMatchDetail(matchId: Long, viewerUserId: Long): Flow<VerifiedMatchDetail?> = flowOf(detail)
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
        override suspend fun respondToInterestAndCreateMatch(actorUserId: Long, interestId: Long, chosenSenderItemId: Long): RespondInterestResult =
            RespondInterestResult.Success(1L)
        override suspend fun declineIncomingInterest(actorUserId: Long, interestId: Long): DeclineInterestResult =
            DeclineInterestResult.Success
        override suspend fun cancelMatch(matchId: Long, actorUserId: Long): CancelMatchResult = CancelMatchResult.Success
        override suspend fun completeMatchExchange(matchId: Long, actorUserId: Long, meetingLocation: String?): CompleteMatchResult =
            CompleteMatchResult.Success
        override suspend fun markNotificationsRead(userId: Long) {}
        override suspend fun markNotificationReadById(notificationId: Long) {}
    }

    private class FakeChatRepo : ChatRepository {
        val messagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
        val markReadCalls = mutableListOf<Pair<Long, Long>>()

        override fun observeChatThreads(userId: Long): Flow<List<ChatThreadSummary>> = flowOf(emptyList())
        override fun observePartnerMatches(userId: Long, partnerUserId: Long): Flow<List<ChatThreadSummary>> =
            flowOf(emptyList())
        override fun observeMessages(matchId: Long, viewerUserId: Long): Flow<List<ChatMessage>> = messagesFlow
        override suspend fun sendMessage(matchId: Long, senderId: Long, text: String): SendMessageResult =
            SendMessageResult.Success(1L)
        override suspend fun markMessagesRead(matchId: Long, viewerUserId: Long) {
            markReadCalls.add(matchId to viewerUserId)
        }
    }

    @Test
    fun addEditClothingViewModel_guardsDoubleSaveAndDoubleDelete() = runTest {
        val gate = CompletableDeferred<Unit>()
        val wardrobeRepo = FakeWardrobeRepo(null).apply { addGate = gate }
        val vm = AddEditClothingViewModel(
            savedStateHandle = SavedStateHandle(),
            authRepository = FakeAuthRepo(sampleProfile()),
            wardrobeRepository = wardrobeRepo
        )
        vm.onNameChanged("Kemeja Linen")

        var successCount = 0
        vm.save { successCount++ }
        assertTrue(vm.uiState.value.isSaving)
        vm.save { successCount++ }
        assertEquals(1, wardrobeRepo.addCalls)

        gate.complete(Unit)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(1, successCount)

        val deleteGate = CompletableDeferred<Unit>()
        val editWardrobeRepo = FakeWardrobeRepo(sampleClothingItem(55L)).apply { this.deleteGate = deleteGate }
        val editVm = AddEditClothingViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to 55L)),
            authRepository = FakeAuthRepo(sampleProfile()),
            wardrobeRepository = editWardrobeRepo
        )
        var deleteCallbacks = 0
        editVm.confirmDelete { deleteCallbacks++ }
        assertTrue(editVm.uiState.value.isDeleting)
        editVm.confirmDelete { deleteCallbacks++ }
        editVm.save { deleteCallbacks++ }
        assertEquals(1, editWardrobeRepo.deleteCalls)

        deleteGate.complete(Unit)
        assertFalse(editVm.uiState.value.isDeleting)
        assertEquals(1, deleteCallbacks)
    }

    @Test
    fun editProfileViewModel_guardsDoubleAvatarAndDoubleSaveAndShowsSizeCopy() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeProfileReviewRepo().apply { avatarGate = gate }
        val vm = EditProfileViewModel(FakeAuthRepo(sampleProfile()), repo)

        vm.onAvatarPicked("content://test/avatar.jpg")
        assertTrue(vm.uiState.value.isUploadingAvatar)
        vm.onAvatarPicked("content://test/avatar2.jpg")
        assertEquals(1, repo.avatarCalls)

        gate.complete(Unit)
        assertFalse(vm.uiState.value.isUploadingAvatar)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("5 MB"))

        val saveGate = CompletableDeferred<Unit>()
        repo.updateProfileGate = saveGate
        var savedCount = 0
        vm.saveProfile { savedCount++ }
        assertTrue(vm.uiState.value.isSaving)
        vm.saveProfile { savedCount++ }
        vm.onAvatarPicked("content://test/avatar3.jpg")
        assertEquals(1, repo.updateProfileCalls)
        assertEquals(1, repo.avatarCalls)

        saveGate.complete(Unit)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(1, savedCount)
    }

    @Test
    fun rateUserAndPreferencesViewModels_guardDoubleSubmit() = runTest {
        val repo = FakeProfileReviewRepo().apply {
            submitReviewGate = CompletableDeferred()
            updatePrefsGate = CompletableDeferred()
        }
        val detail = sampleMatchDetail(99L).copy(status = MatchStatus.COMPLETED)
        val rateVm = RateUserViewModel(
            savedStateHandle = SavedStateHandle(mapOf("matchId" to 99L)),
            authRepository = FakeAuthRepo(sampleProfile()),
            discoveryMatchRepository = FakeDiscoveryMatchRepo(detail),
            profileReviewRepository = repo
        )
        rateVm.selectRating(5)
        rateVm.submitReview()
        assertTrue(rateVm.uiState.value.isSubmitting)
        rateVm.submitReview()
        assertEquals(1, repo.submitReviewCalls)
        repo.submitReviewGate?.complete(Unit)
        assertFalse(rateVm.uiState.value.isSubmitting)
        assertTrue(rateVm.uiState.value.isSuccess)

        val prefsVm = PreferencesViewModel(
            savedStateHandle = SavedStateHandle(),
            authRepository = FakeAuthRepo(sampleProfile()),
            profileReviewRepository = repo
        )
        var prefSaves = 0
        prefsVm.savePreferences { prefSaves++ }
        assertTrue(prefsVm.uiState.value.isSaving)
        prefsVm.savePreferences { prefSaves++ }
        assertEquals(1, repo.updatePrefsCalls)
        repo.updatePrefsGate?.complete(Unit)
        assertFalse(prefsVm.uiState.value.isSaving)
        assertEquals(1, prefSaves)
    }

    @Test
    fun conversationViewModel_autoMarksNewUnreadIncomingMessagesRead() = runTest {
        val chatRepo = FakeChatRepo()
        val detail = sampleMatchDetail(99L)
        ConversationViewModel(
            savedStateHandle = SavedStateHandle(mapOf("matchId" to 99L)),
            authRepository = FakeAuthRepo(sampleProfile()),
            discoveryMatchRepository = FakeDiscoveryMatchRepo(detail),
            chatRepository = chatRepo,
            profileReviewRepository = FakeProfileReviewRepo()
        )
        assertEquals(1, chatRepo.markReadCalls.size)

        val ownMsg = ChatMessage(
            id = 10L,
            matchId = 99L,
            senderId = 10L,
            senderName = "Bagas",
            messageText = "Halo",
            isRead = false,
            sentAt = 100L,
            isFromMe = true
        )
        chatRepo.messagesFlow.value = listOf(ownMsg)
        assertEquals(1, chatRepo.markReadCalls.size)

        val incomingUnread = ChatMessage(
            id = 20L,
            matchId = 99L,
            senderId = 20L,
            senderName = "Rina",
            messageText = "Siap ketemuan",
            isRead = false,
            sentAt = 200L,
            isFromMe = false
        )
        chatRepo.messagesFlow.value = listOf(ownMsg, incomingUnread)
        assertEquals(2, chatRepo.markReadCalls.size)
        assertEquals(99L to 10L, chatRepo.markReadCalls.last())

        chatRepo.messagesFlow.value = listOf(ownMsg, incomingUnread)
        assertEquals(2, chatRepo.markReadCalls.size)
    }
}
