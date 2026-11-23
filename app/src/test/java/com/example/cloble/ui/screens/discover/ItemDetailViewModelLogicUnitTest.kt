package com.example.cloble.ui.screens.discover

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModelLogicUnitTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleProfile(id: Long, name: String): UserProfile = UserProfile(
        id = id,
        name = name,
        email = "user$id@example.com",
        bio = "",
        avatarUri = null,
        city = "Bandung, Jawa Barat",
        latitude = -6.9175,
        longitude = 107.6191,
        preferredSize = "M",
        preferredGender = "Uniseks",
        preferredRadiusKm = 10,
        createdAt = 1000L
    )

    private fun sampleClothing(id: Long, ownerId: Long = 20L): ClothingItem = ClothingItem(
        id = id,
        ownerId = ownerId,
        name = "Jaket Denim",
        description = "Kondisi bagus",
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
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private class FakeAuthRepo(private val userId: Long = 10L) : AuthRepository {
        override fun observeLoggedInUserId(): Flow<Long?> = flowOf(userId)
        override fun observeCurrentUser(): Flow<UserProfile?> = flowOf(null)
        override suspend fun login(email: String, rawPassword: String): AuthResult = AuthResult.InvalidCredentials
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

    private class FakeProfileRepo(
        private val profiles: Map<Long, UserProfile>
    ) : ProfileReviewRepository {
        override fun observeUserProfile(userId: Long, viewerUserId: Long?): Flow<UserProfile?> =
            flowOf(profiles[userId])
        override fun observeUserReviews(userId: Long): Flow<List<UserReview>> = flowOf(emptyList())
        override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> = flowOf(false)
        override suspend fun updatePreferences(userId: Long, prefs: UserPreferences): Boolean = true
        override suspend fun updateBio(userId: Long, bio: String) {}
        override suspend fun updateProfile(userId: Long, update: UserProfileUpdate): Boolean = true
        override suspend fun persistAvatarPhoto(sourceUriString: String): String? = null
        override suspend fun submitReview(matchId: Long, reviewerId: Long, rating: Int, comment: String): ReviewSubmitResult =
            ReviewSubmitResult.Success(1L)
    }

    private class FakeWardrobeRepo(var item: ClothingItem?) : WardrobeRepository {
        var getClothingItemCalls: Int = 0
        var locked: Boolean = false

        override fun observeMyWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeUserPublicWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeClothingItem(itemId: Long): Flow<ClothingItem?> = flowOf(item)
        override suspend fun getClothingItem(itemId: Long): ClothingItem? {
            getClothingItemCalls++
            return item?.takeIf { it.id == itemId }
        }
        override suspend fun isItemLocked(itemId: Long): Boolean = locked
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

    private class FakeDiscoveryRepo(
        val outgoingFlow: MutableStateFlow<List<OutgoingInterestSummary>> = MutableStateFlow(emptyList()),
        var sendResult: SendInterestResult = SendInterestResult.Success(1L)
    ) : DiscoveryMatchRepository {
        override fun observeDiscoverCandidates(viewerUserId: Long, filter: DiscoverFilter): Flow<List<DiscoverCandidate>> =
            flowOf(emptyList())
        override fun observeMatchDetail(matchId: Long, viewerUserId: Long): Flow<VerifiedMatchDetail?> = flowOf(null)
        override fun observeNotifications(userId: Long): Flow<List<NotificationFeedItem>> = flowOf(emptyList())
        override fun observeUnreadNotificationCount(userId: Long): Flow<Int> = flowOf(0)
        override fun observeOutgoingInterests(userId: Long): Flow<List<OutgoingInterestSummary>> = outgoingFlow
        override fun observePassedClothing(userId: Long): Flow<List<PassedClothingSummary>> = flowOf(emptyList())
        override suspend fun passCandidate(userId: Long, targetItemId: Long): Boolean = true
        override suspend fun sendInterest(senderId: Long, targetItemId: Long): SendInterestResult = sendResult
        override suspend fun cancelOutgoingInterest(userId: Long, interestId: Long): CancelInterestResult =
            CancelInterestResult.Success
        override suspend fun convertPassToInterest(userId: Long, targetItemId: Long): SendInterestResult = sendResult
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

    @Test
    fun onScreenResumed_skipsInitialResumeAndRefreshesOnSubsequentResumes() = runTest {
        val wardrobeRepo = FakeWardrobeRepo(sampleClothing(55L))
        val discoveryRepo = FakeDiscoveryRepo()
        val profileRepo = FakeProfileRepo(
            mapOf(10L to sampleProfile(10L, "Bagas"), 20L to sampleProfile(20L, "Pemilik"))
        )
        val vm = ItemDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to 55L)),
            authRepository = FakeAuthRepo(10L),
            wardrobeRepository = wardrobeRepo,
            profileReviewRepository = profileRepo,
            discoveryMatchRepository = discoveryRepo
        )

        assertEquals(1, wardrobeRepo.getClothingItemCalls)
        assertNull(vm.uiState.value.existingInterestStatus)

        vm.onScreenResumed()
        assertEquals(1, wardrobeRepo.getClothingItemCalls)

        discoveryRepo.outgoingFlow.value = listOf(
            OutgoingInterestSummary(
                interestId = 90L,
                targetItemId = 55L,
                targetItemName = "Jaket Denim",
                targetItemCategory = "Luaran",
                targetItemPhotoUri = null,
                targetItemStatus = ItemStatus.AVAILABLE,
                ownerId = 20L,
                ownerName = "Pemilik",
                ownerCity = "Bandung, Jawa Barat",
                interestStatus = InterestStatus.PENDING,
                createdAt = 1000L,
                matchId = null,
                matchStatus = null
            )
        )

        vm.onScreenResumed()
        assertEquals(2, wardrobeRepo.getClothingItemCalls)
        assertEquals(InterestStatus.PENDING, vm.uiState.value.existingInterestStatus)
        assertEquals(90L, vm.uiState.value.existingInterestId)
        assertTrue(vm.uiState.value.interestSentSuccess)
    }

    @Test
    fun refreshDetail_clearsStaleItemAndCtaWhenItemBecomesNull() = runTest {
        val wardrobeRepo = FakeWardrobeRepo(sampleClothing(55L))
        val cancelledSummary = OutgoingInterestSummary(
            interestId = 91L,
            targetItemId = 55L,
            targetItemName = "Jaket Denim",
            targetItemCategory = "Luaran",
            targetItemPhotoUri = null,
            targetItemStatus = ItemStatus.AVAILABLE,
            ownerId = 20L,
            ownerName = "Pemilik",
            ownerCity = "Bandung, Jawa Barat",
            interestStatus = InterestStatus.RESOLVED_BY_MATCH,
            createdAt = 1000L,
            matchId = 30L,
            matchStatus = MatchStatus.CANCELLED
        )
        val discoveryRepo = FakeDiscoveryRepo(MutableStateFlow(listOf(cancelledSummary)))
        val profileRepo = FakeProfileRepo(
            mapOf(10L to sampleProfile(10L, "Bagas"), 20L to sampleProfile(20L, "Pemilik"))
        )
        val vm = ItemDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to 55L)),
            authRepository = FakeAuthRepo(10L),
            wardrobeRepository = wardrobeRepo,
            profileReviewRepository = profileRepo,
            discoveryMatchRepository = discoveryRepo
        )

        assertTrue(vm.uiState.value.canReofferAfterCancelled)
        assertEquals(55L, vm.uiState.value.item?.id)

        wardrobeRepo.item = null
        vm.refreshDetail()

        assertNull(vm.uiState.value.item)
        assertNull(vm.uiState.value.owner)
        assertFalse(vm.uiState.value.canReofferAfterCancelled)
        assertTrue(vm.uiState.value.isItemUnavailable)
        assertEquals("Pakaian tidak ditemukan.", vm.uiState.value.errorMessage)

        wardrobeRepo.item = sampleClothing(55L)
        vm.refreshDetail()

        assertEquals(55L, vm.uiState.value.item?.id)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun sendInterest_clearsCanReofferAfterCancelledOnTargetItemNotEligible() = runTest {
        val wardrobeRepo = FakeWardrobeRepo(sampleClothing(55L))
        val cancelledSummary = OutgoingInterestSummary(
            interestId = 92L,
            targetItemId = 55L,
            targetItemName = "Jaket Denim",
            targetItemCategory = "Luaran",
            targetItemPhotoUri = null,
            targetItemStatus = ItemStatus.AVAILABLE,
            ownerId = 20L,
            ownerName = "Pemilik",
            ownerCity = "Bandung, Jawa Barat",
            interestStatus = InterestStatus.RESOLVED_BY_MATCH,
            createdAt = 1000L,
            matchId = 31L,
            matchStatus = MatchStatus.CANCELLED
        )
        val discoveryRepo = FakeDiscoveryRepo(
            outgoingFlow = MutableStateFlow(listOf(cancelledSummary)),
            sendResult = SendInterestResult.TargetItemNotEligible
        )
        val profileRepo = FakeProfileRepo(
            mapOf(10L to sampleProfile(10L, "Bagas"), 20L to sampleProfile(20L, "Pemilik"))
        )
        val vm = ItemDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to 55L)),
            authRepository = FakeAuthRepo(10L),
            wardrobeRepository = wardrobeRepo,
            profileReviewRepository = profileRepo,
            discoveryMatchRepository = discoveryRepo
        )

        assertTrue(vm.uiState.value.canReofferAfterCancelled)
        vm.sendInterest()

        assertTrue(vm.uiState.value.isItemUnavailable)
        assertFalse(vm.uiState.value.canReofferAfterCancelled)
        assertEquals("Pakaian ini sudah tidak tersedia untuk dibarter.", vm.uiState.value.errorMessage)
    }
}
