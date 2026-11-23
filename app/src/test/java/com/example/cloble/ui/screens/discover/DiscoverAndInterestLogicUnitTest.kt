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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
class DiscoverAndInterestLogicUnitTest {

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    private fun sampleUser(id: Long, name: String) = UserProfile(
        id = id,
        name = name,
        email = "$name@example.com",
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

    private fun sampleItem(
        id: Long,
        ownerId: Long = 2L,
        isBarterAllowed: Boolean = true,
        status: ItemStatus = ItemStatus.AVAILABLE
    ) = ClothingItem(
        id = id,
        ownerId = ownerId,
        name = "Jaket Denim $id",
        description = "Kondisi bagus",
        category = "Luaran",
        size = "M",
        gender = "Uniseks",
        color = "Biru",
        condition = "Baik",
        mainPhotoUri = null,
        detailPhotoUri = null,
        extraPhotoUri = null,
        isBarterAllowed = isBarterAllowed,
        status = status,
        createdAt = 1000L,
        updatedAt = 2000L
    )

    private class FakeAuthRepository(user: UserProfile) : AuthRepository {
        private val userIdFlow = MutableStateFlow<Long?>(user.id)
        private val userFlow = MutableStateFlow<UserProfile?>(user)

        override fun observeLoggedInUserId(): Flow<Long?> = userIdFlow
        override fun observeCurrentUser(): Flow<UserProfile?> = userFlow
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

    private class FakeDiscoveryMatchRepository(
        candidates: List<DiscoverCandidate> = emptyList(),
        var outgoing: List<OutgoingInterestSummary> = emptyList(),
        var passResult: Boolean = true,
        var sendInterestResult: SendInterestResult = SendInterestResult.Success(10L, null),
        private val sendGate: CompletableDeferred<Unit>? = null,
        private val passGate: CompletableDeferred<Unit>? = null
    ) : DiscoveryMatchRepository {
        private val candidatesFlow = MutableStateFlow(candidates)
        var sendInterestCalls = 0
        var passCandidateCalls = 0

        override fun observeDiscoverCandidates(viewerUserId: Long, filter: DiscoverFilter): Flow<List<DiscoverCandidate>> =
            candidatesFlow
        override fun observeMatchDetail(matchId: Long, viewerUserId: Long): Flow<VerifiedMatchDetail?> = flowOf(null)
        override fun observeNotifications(userId: Long): Flow<List<NotificationFeedItem>> = flowOf(emptyList())
        override fun observeUnreadNotificationCount(userId: Long): Flow<Int> = flowOf(0)
        override fun observeOutgoingInterests(userId: Long): Flow<List<OutgoingInterestSummary>> = flowOf(outgoing)
        override fun observePassedClothing(userId: Long): Flow<List<PassedClothingSummary>> = flowOf(emptyList())

        override suspend fun passCandidate(userId: Long, targetItemId: Long): Boolean {
            passCandidateCalls += 1
            passGate?.await()
            return passResult
        }

        override suspend fun sendInterest(senderId: Long, targetItemId: Long): SendInterestResult {
            sendInterestCalls += 1
            sendGate?.await()
            return sendInterestResult
        }

        override suspend fun cancelOutgoingInterest(userId: Long, interestId: Long) = CancelInterestResult.Success
        override suspend fun convertPassToInterest(userId: Long, targetItemId: Long) = sendInterestResult
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

    private class FakeWardrobeRepository(
        private val item: ClothingItem?,
        private val locked: Boolean = false,
        var eligibleBarterCount: Int = 1
    ) : WardrobeRepository {
        override fun observeMyWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeUserPublicWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeClothingItem(itemId: Long): Flow<ClothingItem?> = flowOf(item)
        override suspend fun getClothingItem(itemId: Long): ClothingItem? = item
        override suspend fun isItemLocked(itemId: Long): Boolean = locked
        override suspend fun addClothingItem(ownerId: Long, draft: ClothingDraft) = WardrobeMutationResult.ItemNotFound
        override suspend fun updateClothingItem(actorUserId: Long, itemId: Long, draft: ClothingDraft) =
            WardrobeMutationResult.ItemNotFound
        override suspend fun setBarterAllowed(actorUserId: Long, itemId: Long, allowed: Boolean) =
            WardrobeMutationResult.ItemNotFound
        override suspend fun softDeleteClothingItem(actorUserId: Long, itemId: Long) =
            WardrobeMutationResult.ItemNotFound
        override suspend fun persistLocalPhoto(sourceUriString: String): String? = null
        override suspend fun countEligibleBarterItems(ownerId: Long): Int = eligibleBarterCount
    }

    private class FakeProfileReviewRepository(
        private val profiles: Map<Long, UserProfile>
    ) : ProfileReviewRepository {
        override fun observeUserProfile(userId: Long, viewerUserId: Long?): Flow<UserProfile?> =
            flowOf(profiles[userId])
        override fun observeUserReviews(userId: Long): Flow<List<UserReview>> = flowOf(emptyList())
        override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> = flowOf(false)
        override suspend fun updatePreferences(userId: Long, prefs: UserPreferences) = true
        override suspend fun updateBio(userId: Long, bio: String) {}
        override suspend fun updateProfile(userId: Long, update: UserProfileUpdate) = true
        override suspend fun persistAvatarPhoto(sourceUriString: String): String? = null
        override suspend fun submitReview(matchId: Long, reviewerId: Long, rating: Int, comment: String) =
            ReviewSubmitResult.MatchNotFound
    }

    @Test
    fun passCurrentCandidate_whenRepositoryReturnsFalse_retainsCandidateAndShowsNetworkError() = runTest {
        val candidate = DiscoverCandidate(
            item = sampleItem(id = 101L),
            ownerName = "Naya",
            ownerCity = "Bandung",
            ownerAverageRating = 4.8,
            ownerReviewCount = 3,
            distanceKm = 2
        )
        val repo = FakeDiscoveryMatchRepository(candidates = listOf(candidate), passResult = false)
        val viewModel = DiscoverViewModel(
            authRepository = FakeAuthRepository(sampleUser(1L, "Raka")),
            discoveryMatchRepository = repo
        )

        viewModel.passCurrentCandidate()

        val state = viewModel.uiState.value
        assertEquals(1, repo.passCandidateCalls)
        assertEquals(1, state.candidates.size)
        assertEquals(101L, state.currentCandidate?.item?.id)
        assertEquals("Gagal menyimpan aksi lewati. Periksa koneksi internet Anda.", state.bannerMessage)
    }

    @Test
    fun likeCurrentCandidate_whenPairAlreadyMatched_showsSpecificMessage() = runTest {
        val candidate = DiscoverCandidate(
            item = sampleItem(id = 102L),
            ownerName = "Naya",
            ownerCity = "Bandung",
            ownerAverageRating = 5.0,
            ownerReviewCount = 1,
            distanceKm = 3
        )
        val repo = FakeDiscoveryMatchRepository(
            candidates = listOf(candidate),
            sendInterestResult = SendInterestResult.PairAlreadyMatched
        )
        val viewModel = DiscoverViewModel(
            authRepository = FakeAuthRepository(sampleUser(1L, "Raka")),
            discoveryMatchRepository = repo
        )

        viewModel.likeCurrentCandidate()

        val state = viewModel.uiState.value
        assertEquals(
            "Pasangan pakaian ini sudah memiliki pertukaran aktif atau sudah selesai ditukar.",
            state.bannerMessage
        )
        assertFalse(state.requiresBarterItemCta)
    }

    @Test
    fun likeCurrentCandidate_whenMutualMatchReturned_emitsOpenMatchEvent() = runTest {
        val candidate = DiscoverCandidate(
            item = sampleItem(id = 103L),
            ownerName = "Naya",
            ownerCity = "Bandung",
            ownerAverageRating = 5.0,
            ownerReviewCount = 1,
            distanceKm = 1
        )
        val repo = FakeDiscoveryMatchRepository(
            candidates = listOf(candidate),
            sendInterestResult = SendInterestResult.Success(interestId = 55L, matchId = 77L)
        )
        val viewModel = DiscoverViewModel(
            authRepository = FakeAuthRepository(sampleUser(1L, "Raka")),
            discoveryMatchRepository = repo
        )

        val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.likeCurrentCandidate()

        val event = eventDeferred.await()
        assertEquals(DiscoverNavigationEvent.OpenMatch(77L), event)
        assertTrue(viewModel.uiState.value.candidates.isEmpty())
    }

    @Test
    fun likeCurrentCandidate_whileActionInProgress_ignoresDuplicateDoubleTap() = runTest {
        val candidate = DiscoverCandidate(
            item = sampleItem(id = 104L),
            ownerName = "Naya",
            ownerCity = "Bandung",
            ownerAverageRating = 5.0,
            ownerReviewCount = 1,
            distanceKm = 1
        )
        val gate = CompletableDeferred<Unit>()
        val repo = FakeDiscoveryMatchRepository(
            candidates = listOf(candidate),
            sendInterestResult = SendInterestResult.Success(interestId = 88L, matchId = null),
            sendGate = gate
        )
        val viewModel = DiscoverViewModel(
            authRepository = FakeAuthRepository(sampleUser(1L, "Raka")),
            discoveryMatchRepository = repo
        )

        viewModel.likeCurrentCandidate()
        assertTrue(viewModel.uiState.value.isActionInProgress)

        viewModel.likeCurrentCandidate()
        viewModel.passCurrentCandidate()

        assertEquals(1, repo.sendInterestCalls)
        assertEquals(0, repo.passCandidateCalls)

        gate.complete(Unit)
        assertFalse(viewModel.uiState.value.isActionInProgress)
    }

    @Test
    fun likeCurrentCandidate_beforeCoroutineStarts_blocksDuplicateLikeAndPass() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val candidate = DiscoverCandidate(
            item = sampleItem(id = 105L), ownerName = "Naya", ownerCity = "Bandung",
            ownerAverageRating = 5.0, ownerReviewCount = 1, distanceKm = 1
        )
        val gate = CompletableDeferred<Unit>()
        val repo = FakeDiscoveryMatchRepository(candidates = listOf(candidate), sendGate = gate)
        val viewModel = DiscoverViewModel(
            authRepository = FakeAuthRepository(sampleUser(1L, "Raka")),
            discoveryMatchRepository = repo
        )
        runCurrent()

        try {
            viewModel.likeCurrentCandidate()
            viewModel.likeCurrentCandidate()
            viewModel.passCurrentCandidate()
            runCurrent()

            assertEquals(1, repo.sendInterestCalls)
            assertEquals(0, repo.passCandidateCalls)
            assertTrue(viewModel.uiState.value.isActionInProgress)
        } finally {
            gate.complete(Unit)
            runCurrent()
        }
        assertFalse(viewModel.uiState.value.isActionInProgress)
        assertTrue(viewModel.uiState.value.candidates.isEmpty())
    }

    @Test
    fun passCurrentCandidate_beforeCoroutineStarts_blocksDuplicatePassAndLike_thenAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val candidate = DiscoverCandidate(
            item = sampleItem(id = 106L), ownerName = "Naya", ownerCity = "Bandung",
            ownerAverageRating = 5.0, ownerReviewCount = 1, distanceKm = 1
        )
        val gate = CompletableDeferred<Unit>()
        val repo = FakeDiscoveryMatchRepository(
            candidates = listOf(candidate), passResult = false, passGate = gate
        )
        val viewModel = DiscoverViewModel(
            authRepository = FakeAuthRepository(sampleUser(1L, "Raka")),
            discoveryMatchRepository = repo
        )
        runCurrent()

        try {
            viewModel.passCurrentCandidate()
            viewModel.passCurrentCandidate()
            viewModel.likeCurrentCandidate()
            runCurrent()

            assertEquals(1, repo.passCandidateCalls)
            assertEquals(0, repo.sendInterestCalls)
            assertTrue(viewModel.uiState.value.isActionInProgress)
        } finally {
            gate.complete(Unit)
            runCurrent()
        }
        assertFalse(viewModel.uiState.value.isActionInProgress)
        assertEquals(106L, viewModel.uiState.value.currentCandidate?.item?.id)
        assertEquals("Gagal menyimpan aksi lewati. Periksa koneksi internet Anda.", viewModel.uiState.value.bannerMessage)

        repo.passResult = true
        viewModel.passCurrentCandidate()
        runCurrent()
        assertEquals(2, repo.passCandidateCalls)
        assertTrue(viewModel.uiState.value.candidates.isEmpty())
        assertFalse(viewModel.uiState.value.isActionInProgress)
    }

    @Test
    fun itemDetail_withInvalidatedPriorInterest_andAvailableItem_allowsReoffer() = runTest {
        val targetItem = sampleItem(id = 201L, ownerId = 2L, isBarterAllowed = true, status = ItemStatus.AVAILABLE)
        val repo = FakeDiscoveryMatchRepository(
            outgoing = listOf(
                OutgoingInterestSummary(
                    interestId = 31L,
                    targetItemId = 201L,
                    targetItemName = targetItem.name,
                    targetItemCategory = targetItem.category,
                    targetItemPhotoUri = null,
                    targetItemStatus = ItemStatus.AVAILABLE,
                    ownerId = 2L,
                    ownerName = "Naya",
                    ownerCity = "Bandung",
                    interestStatus = InterestStatus.INVALIDATED,
                    createdAt = 1000L,
                    matchId = null,
                    matchStatus = null,
                    isLockedByOtherMatch = false
                )
            ),
            sendInterestResult = SendInterestResult.Success(interestId = 42L, matchId = null)
        )
        val viewModel = ItemDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to 201L)),
            authRepository = FakeAuthRepository(sampleUser(1L, "Raka")),
            wardrobeRepository = FakeWardrobeRepository(targetItem, locked = false),
            profileReviewRepository = FakeProfileReviewRepository(
                mapOf(1L to sampleUser(1L, "Raka"), 2L to sampleUser(2L, "Naya"))
            ),
            discoveryMatchRepository = repo
        )

        assertTrue(viewModel.uiState.value.canReofferAfterInvalidated)
        assertFalse(viewModel.uiState.value.isLockedByOtherMatch)
        assertFalse(viewModel.uiState.value.isItemUnavailable)

        viewModel.sendInterest()

        assertEquals(1, repo.sendInterestCalls)
        assertTrue(viewModel.uiState.value.interestSentSuccess)
        assertEquals(42L, viewModel.uiState.value.existingInterestId)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun itemDetail_withInvalidatedPriorInterest_andUnavailableItem_blocksReoffer() = runTest {
        val targetItem = sampleItem(id = 202L, ownerId = 2L, isBarterAllowed = false, status = ItemStatus.AVAILABLE)
        val repo = FakeDiscoveryMatchRepository(
            outgoing = listOf(
                OutgoingInterestSummary(
                    interestId = 32L,
                    targetItemId = 202L,
                    targetItemName = targetItem.name,
                    targetItemCategory = targetItem.category,
                    targetItemPhotoUri = null,
                    targetItemStatus = ItemStatus.AVAILABLE,
                    ownerId = 2L,
                    ownerName = "Naya",
                    ownerCity = "Bandung",
                    interestStatus = InterestStatus.INVALIDATED,
                    createdAt = 1000L,
                    matchId = null,
                    matchStatus = null,
                    isLockedByOtherMatch = false
                )
            )
        )
        val viewModel = ItemDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to 202L)),
            authRepository = FakeAuthRepository(sampleUser(1L, "Raka")),
            wardrobeRepository = FakeWardrobeRepository(targetItem, locked = false),
            profileReviewRepository = FakeProfileReviewRepository(
                mapOf(1L to sampleUser(1L, "Raka"), 2L to sampleUser(2L, "Naya"))
            ),
            discoveryMatchRepository = repo
        )

        assertFalse(viewModel.uiState.value.canReofferAfterInvalidated)
        assertTrue(viewModel.uiState.value.isItemUnavailable)

        viewModel.sendInterest()

        assertEquals(0, repo.sendInterestCalls)
    }

    @Test
    fun discoverViewModel_refreshDiscover_clearsRequiresBarterCtaAfterAddingEligibleItem() = runTest {
        val candidate = DiscoverCandidate(
            item = sampleItem(id = 105L),
            ownerName = "Naya",
            ownerCity = "Bandung",
            ownerAverageRating = 4.5,
            ownerReviewCount = 2,
            distanceKm = 4
        )
        val repo = FakeDiscoveryMatchRepository(
            candidates = listOf(candidate),
            sendInterestResult = SendInterestResult.RequiresBarterableItemInWardrobe
        )
        val wardrobeRepo = FakeWardrobeRepository(item = null, eligibleBarterCount = 0)
        val viewModel = DiscoverViewModel(
            authRepository = FakeAuthRepository(sampleUser(1L, "Raka")),
            discoveryMatchRepository = repo,
            wardrobeRepository = wardrobeRepo
        )

        viewModel.likeCurrentCandidate()
        assertTrue(viewModel.uiState.value.requiresBarterItemCta)
        assertTrue(viewModel.uiState.value.bannerMessage != null)

        wardrobeRepo.eligibleBarterCount = 1
        viewModel.refreshDiscover()

        assertFalse(viewModel.uiState.value.requiresBarterItemCta)
        assertNull(viewModel.uiState.value.bannerMessage)
    }

    @Test
    fun itemDetail_withResolvedReceiverMatch_allowsOpeningChatAndNotLockedByOther() = runTest {
        val targetItem = sampleItem(id = 203L, ownerId = 2L, isBarterAllowed = false, status = ItemStatus.EXCHANGED)
        val repo = FakeDiscoveryMatchRepository(
            outgoing = listOf(
                OutgoingInterestSummary(
                    interestId = 33L,
                    targetItemId = 203L,
                    targetItemName = targetItem.name,
                    targetItemCategory = targetItem.category,
                    targetItemPhotoUri = null,
                    targetItemStatus = ItemStatus.EXCHANGED,
                    ownerId = 2L,
                    ownerName = "Naya",
                    ownerCity = "Bandung",
                    interestStatus = InterestStatus.RESOLVED_BY_MATCH,
                    createdAt = 1000L,
                    matchId = 88L,
                    matchStatus = MatchStatus.COMPLETED,
                    isLockedByOtherMatch = false
                )
            )
        )
        val viewModel = ItemDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to 203L)),
            authRepository = FakeAuthRepository(sampleUser(1L, "Raka")),
            wardrobeRepository = FakeWardrobeRepository(targetItem, locked = false),
            profileReviewRepository = FakeProfileReviewRepository(
                mapOf(1L to sampleUser(1L, "Raka"), 2L to sampleUser(2L, "Naya"))
            ),
            discoveryMatchRepository = repo
        )

        assertFalse(viewModel.uiState.value.isLockedByOtherMatch)
        assertEquals(88L, viewModel.uiState.value.relatedMatchId)
        assertEquals(MatchStatus.COMPLETED, viewModel.uiState.value.relatedMatchStatus)
    }

    @Test
    fun itemDetail_withCancelledMatchAndOtherActiveMatch_marksLockedByOtherMatch() = runTest {
        val targetItem = sampleItem(id = 204L, ownerId = 2L, isBarterAllowed = true, status = ItemStatus.AVAILABLE)
        val repo = FakeDiscoveryMatchRepository(
            outgoing = listOf(
                OutgoingInterestSummary(
                    interestId = 34L,
                    targetItemId = 204L,
                    targetItemName = targetItem.name,
                    targetItemCategory = targetItem.category,
                    targetItemPhotoUri = null,
                    targetItemStatus = ItemStatus.AVAILABLE,
                    ownerId = 2L,
                    ownerName = "Naya",
                    ownerCity = "Bandung",
                    interestStatus = InterestStatus.ACCEPTED_MATCHED,
                    createdAt = 1000L,
                    matchId = 50L,
                    matchStatus = MatchStatus.CANCELLED,
                    isLockedByOtherMatch = true
                )
            )
        )
        val viewModel = ItemDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to 204L)),
            authRepository = FakeAuthRepository(sampleUser(1L, "Raka")),
            wardrobeRepository = FakeWardrobeRepository(targetItem, locked = true),
            profileReviewRepository = FakeProfileReviewRepository(
                mapOf(1L to sampleUser(1L, "Raka"), 2L to sampleUser(2L, "Naya"))
            ),
            discoveryMatchRepository = repo
        )

        assertTrue(viewModel.uiState.value.isLockedByOtherMatch)
        assertFalse(viewModel.uiState.value.canReofferAfterCancelled)
    }

    @Test
    fun itemDetail_reofferWithStaleCancelledMatch_refreshesExistingOfferAndStopsRetrying() = runTest {
        for (status in listOf(InterestStatus.PENDING, InterestStatus.DECLINED)) {
            val target = sampleItem(id = 204L, ownerId = 2L)
            val old = OutgoingInterestSummary(
                interestId = 34L, targetItemId = 204L, targetItemName = target.name,
                targetItemCategory = target.category, targetItemPhotoUri = null,
                targetItemStatus = ItemStatus.AVAILABLE, ownerId = 2L,
                ownerName = "Naya", ownerCity = "Bandung", interestStatus = InterestStatus.ACCEPTED_MATCHED,
                createdAt = 1000L, matchId = 50L, matchStatus = MatchStatus.CANCELLED
            )
            val repo = FakeDiscoveryMatchRepository(
                outgoing = listOf(old), sendInterestResult = SendInterestResult.AlreadyInterested(status)
            )
            val viewModel = ItemDetailViewModel(
                SavedStateHandle(mapOf("itemId" to 204L)), FakeAuthRepository(sampleUser(1L, "Raka")),
                FakeWardrobeRepository(target),
                FakeProfileReviewRepository(mapOf(1L to sampleUser(1L, "Raka"), 2L to sampleUser(2L, "Naya"))), repo
            )
            assertTrue(viewModel.uiState.value.canReofferAfterCancelled)
            repo.outgoing = listOf(old.copy(interestId = 35L, interestStatus = status, matchId = null, matchStatus = null))
            viewModel.sendInterest()
            val state = viewModel.uiState.value
            assertFalse(state.canReofferAfterCancelled)
            assertEquals(35L, state.existingInterestId)
            assertEquals(status, state.existingInterestStatus)
            assertNull(state.relatedMatchId)
            viewModel.sendInterest()
            assertEquals(1, repo.sendInterestCalls)
        }
    }
}
