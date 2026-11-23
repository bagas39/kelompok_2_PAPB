package com.example.cloble

import androidx.lifecycle.SavedStateHandle
import com.example.cloble.data.remote.NominatimGeocoder
import com.example.cloble.data.remote.calculateInSampleSize
import com.example.cloble.data.remote.mapSoftDeleteCode
import com.example.cloble.domain.model.CloBleConstants
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
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.util.DistanceCalculator
import com.example.cloble.ui.screens.preferences.PreferencesViewModel
import com.example.cloble.ui.screens.profile.EditProfileViewModel
import com.example.cloble.ui.screens.profile.MyProfileViewModel
import com.example.cloble.ui.screens.review.RateUserViewModel
import com.example.cloble.ui.screens.wardrobe.AddEditClothingViewModel
import com.example.cloble.ui.screens.wardrobe.WardrobeViewModel
import com.example.cloble.ui.screens.wardrobe.normalizePhotoSlots
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HaqiTasksUnitTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleProfile(id: Long = 10L): UserProfile = UserProfile(
        id = id,
        name = "Haqi",
        email = "haqi@example.com",
        bio = "Barter koleksi pribadi",
        avatarUri = null,
        city = "Bandung, Jawa Barat",
        latitude = -6.9175,
        longitude = 107.6191,
        preferredSize = "M",
        preferredGender = "Uniseks",
        preferredRadiusKm = 10,
        createdAt = 1700000000000L
    )

    private fun sampleItem(
        id: Long = 55L,
        mainPhotoUri: String? = null,
        detailPhotoUri: String? = null,
        extraPhotoUri: String? = null
    ): ClothingItem = ClothingItem(
        id = id,
        ownerId = 10L,
        name = "Kemeja Flanel",
        description = "Kondisi mulus",
        category = "Atasan",
        size = "M",
        gender = "Uniseks",
        color = "Hijau",
        condition = "Baik",
        mainPhotoUri = mainPhotoUri,
        detailPhotoUri = detailPhotoUri,
        extraPhotoUri = extraPhotoUri,
        isBarterAllowed = true,
        status = ItemStatus.AVAILABLE,
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L
    )

    private class FakeAuthRepo(
        var userIdFlow: Flow<Long?> = flowOf(10L),
        var userProfileFlow: Flow<UserProfile?> = flowOf(null)
    ) : AuthRepository {
        override fun observeLoggedInUserId(): Flow<Long?> = userIdFlow
        override fun observeCurrentUser(): Flow<UserProfile?> = userProfileFlow
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
        ): AuthResult = AuthResult.InvalidCredentials
        override suspend fun logout() {}
    }

    private class FakeWardrobeRepo(private val existing: ClothingItem? = null) : WardrobeRepository {
        val savedDrafts = mutableListOf<ClothingDraft>()
        val updatedDrafts = mutableListOf<ClothingDraft>()

        override fun observeMyWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeUserPublicWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeClothingItem(itemId: Long): Flow<ClothingItem?> = flowOf(existing)
        override suspend fun getClothingItem(itemId: Long): ClothingItem? = existing
        override suspend fun isItemLocked(itemId: Long): Boolean = false

        override suspend fun addClothingItem(ownerId: Long, draft: ClothingDraft): WardrobeMutationResult {
            savedDrafts.add(draft)
            return WardrobeMutationResult.Success(101L)
        }

        override suspend fun updateClothingItem(
            actorUserId: Long,
            itemId: Long,
            draft: ClothingDraft
        ): WardrobeMutationResult {
            updatedDrafts.add(draft)
            return WardrobeMutationResult.Success(itemId)
        }

        override suspend fun setBarterAllowed(actorUserId: Long, itemId: Long, allowed: Boolean): WardrobeMutationResult =
            WardrobeMutationResult.Success(itemId)

        override suspend fun softDeleteClothingItem(actorUserId: Long, itemId: Long): WardrobeMutationResult =
            WardrobeMutationResult.Success(itemId)

        override suspend fun persistLocalPhoto(sourceUriString: String): String? = sourceUriString
        override suspend fun countEligibleBarterItems(ownerId: Long): Int = 1
    }

    private class FakeProfileReviewRepo : ProfileReviewRepository {
        val updatedPreferences = mutableListOf<Pair<Long, UserPreferences>>()
        val updatedProfiles = mutableListOf<Pair<Long, UserProfileUpdate>>()

        override fun observeUserProfile(userId: Long, viewerUserId: Long?): Flow<UserProfile?> = flowOf(null)
        override fun observeUserReviews(userId: Long): Flow<List<UserReview>> = flowOf(emptyList())
        override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> = flowOf(false)

        override suspend fun updatePreferences(userId: Long, prefs: UserPreferences): Boolean {
            updatedPreferences.add(userId to prefs)
            return true
        }

        override suspend fun updateBio(userId: Long, bio: String) {}

        override suspend fun updateProfile(userId: Long, update: UserProfileUpdate): Boolean {
            updatedProfiles.add(userId to update)
            return true
        }

        override suspend fun persistAvatarPhoto(sourceUriString: String): String? = sourceUriString

        override suspend fun submitReview(
            matchId: Long,
            reviewerId: Long,
            rating: Int,
            comment: String
        ): ReviewSubmitResult = ReviewSubmitResult.Success(1L)
    }

    private class FakeDiscoveryMatchRepo : DiscoveryMatchRepository {
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
        override suspend fun completeMatchExchange(
            matchId: Long,
            actorUserId: Long,
            meetingLocation: String?
        ): CompleteMatchResult = CompleteMatchResult.Success
        override suspend fun markNotificationsRead(userId: Long) {}
        override suspend fun markNotificationReadById(notificationId: Long) {}
    }

    @Test
    fun distanceCalculator_findCityCenter_usesWholeWordMatching() {
        assertNull(DistanceCalculator.findCityCenter("Batujajar"))
        assertNull(DistanceCalculator.findCityCenter("Batuceper"))
        assertNull(DistanceCalculator.findCityCenter("Baturaden"))
        assertNull(DistanceCalculator.findCityCenter("Batubara"))
        assertNull(DistanceCalculator.findCityCenter("Solok"))
        assertNull(DistanceCalculator.findCityCenter("Palur"))
        assertNull(DistanceCalculator.findCityCenter("Palabuhanratu"))
        assertNull(DistanceCalculator.findCityCenter("Tegallega"))

        val batujajarBandungBarat = DistanceCalculator.findCityCenter("Batujajar, Bandung Barat")
        assertNotNull(batujajarBandungBarat)
        assertEquals(-6.9175, batujajarBandungBarat!!.first, 0.0001)

        val kotaBatu = DistanceCalculator.findCityCenter("Kota Batu, Jawa Timur")
        assertNotNull(kotaBatu)
        assertEquals(-7.8671, kotaBatu!!.first, 0.0001)

        val solo = DistanceCalculator.findCityCenter("Solo, Jawa Tengah")
        assertNotNull(solo)
        assertEquals(-7.5755, solo!!.first, 0.0001)

        val palu = DistanceCalculator.findCityCenter("Palu")
        assertNotNull(palu)
        assertEquals(-0.8917, palu!!.first, 0.0001)

        val tegal = DistanceCalculator.findCityCenter("Tegal")
        assertNotNull(tegal)
        assertEquals(-6.8694, tegal!!.first, 0.0001)

        val bandarLampung = DistanceCalculator.findCityCenter("Bandar Lampung")
        assertNotNull(bandarLampung)
        assertEquals(-5.4500, bandarLampung!!.first, 0.0001)
    }

    @Test
    fun distanceCalculator_rejectsNonFiniteAndOutOfRangeCoordinates() {
        val invalidCoords = listOf(
            Double.NaN to 107.6191,
            -6.9175 to Double.NaN,
            Double.POSITIVE_INFINITY to 107.6191,
            -6.9175 to Double.NEGATIVE_INFINITY,
            95.0 to 107.6191,
            -91.0 to 107.6191,
            -6.9175 to 181.0,
            -6.9175 to -181.0
        )

        invalidCoords.forEach { (lat, lon) ->
            assertFalse(DistanceCalculator.isValidCoordinate(lat, lon))
            val resolved = DistanceCalculator.resolveEffectiveCoordinates("Kota Tidak Dikenal", lat, lon)
            assertEquals(-6.9175, resolved.first, 0.0001)
            assertEquals(107.6191, resolved.second, 0.0001)
            assertEquals(0, DistanceCalculator.calculateKm(lat, lon, -6.2088, 106.8456))
            assertTrue(DistanceCalculator.calculateKmExact(lat, lon, -6.2088, 106.8456).isNaN())
        }

        val resolvedUnset = DistanceCalculator.resolveEffectiveCoordinates("Kota Tidak Dikenal", 0.0, 0.0)
        assertEquals(-6.9175, resolvedUnset.first, 0.0001)
        assertEquals(107.6191, resolvedUnset.second, 0.0001)
    }

    @Test
    fun nominatimGeocoder_treatsJsonNullAsEmptyNotLiteralNull() {
        val searchJson = """
            [
              {
                "lat": "-6.9175",
                "lon": "107.6191",
                "display_name": null,
                "address": {
                  "suburb": null,
                  "city": "Kota Bandung",
                  "state": null
                }
              },
              {
                "lat": "-6.2088",
                "lon": "106.8456",
                "display_name": null,
                "address": {
                  "suburb": null,
                  "city": null,
                  "state": null
                }
              }
            ]
        """.trimIndent()

        val searchResults = NominatimGeocoder.parseSearchJson(searchJson)
        assertEquals(1, searchResults.size)
        assertEquals("Kota Bandung", searchResults[0].shortLabel)
        assertEquals("Kota Bandung", searchResults[0].displayName)
        assertFalse(searchResults[0].shortLabel.contains("null", ignoreCase = true))
        assertFalse(searchResults[0].displayName.contains("null", ignoreCase = true))

        val reverseJson = """
            {
              "lat": "-7.7956",
              "lon": "110.3695",
              "display_name": null,
              "address": {
                "suburb": null,
                "city_district": "Depok",
                "county": "Sleman",
                "state": null
              }
            }
        """.trimIndent()

        val reverseResult = NominatimGeocoder.parseReverseJson(reverseJson, -7.79, 110.36)
        assertNotNull(reverseResult)
        assertEquals("Depok, Sleman", reverseResult!!.shortLabel)
        assertEquals("Depok, Sleman", reverseResult.displayName)
        assertFalse(reverseResult.shortLabel.contains("null", ignoreCase = true))
    }

    @Test
    fun calculateInSampleSize_computesPowerOfTwoSubsampling() {
        assertEquals(4, calculateInSampleSize(8000, 6000, 1600))
        assertEquals(2, calculateInSampleSize(4000, 3000, 1600))
        assertEquals(1, calculateInSampleSize(1200, 900, 1600))
        assertEquals(1, calculateInSampleSize(0, 0, 1600))
    }

    @Test
    fun mapSoftDeleteCode_mapsItemNotFoundAndDatabaseErrorsExplicitly() {
        assertEquals(WardrobeMutationResult.Success(12L), mapSoftDeleteCode("SUCCESS", 12L))
        assertEquals(WardrobeMutationResult.InvalidOwnership, mapSoftDeleteCode("INVALID_OWNERSHIP", 12L))
        assertEquals(WardrobeMutationResult.LockedInActiveMatch, mapSoftDeleteCode("LOCKED_IN_ACTIVE_MATCH", 12L))
        assertEquals(WardrobeMutationResult.ItemNotFound, mapSoftDeleteCode("ITEM_NOT_FOUND", 12L))
        val unexpected = mapSoftDeleteCode("UNAUTHORIZED", 12L)
        assertTrue(unexpected is WardrobeMutationResult.DatabaseError)
    }

    @Test
    fun addEditClothingViewModel_normalizesPhotoSlotsOnRemoveAndSave() = runTest {
        assertEquals(
            Triple("https://img/detail.jpg", "https://img/extra.jpg", null),
            normalizePhotoSlots(null, "https://img/detail.jpg", "https://img/extra.jpg")
        )
        assertEquals(
            Triple("https://img/extra.jpg", null, null),
            normalizePhotoSlots("", " ", "https://img/extra.jpg")
        )

        val wardrobeRepo = FakeWardrobeRepo()
        val vm = AddEditClothingViewModel(
            savedStateHandle = SavedStateHandle(),
            authRepository = FakeAuthRepo(flowOf(10L), flowOf(sampleProfile())),
            wardrobeRepository = wardrobeRepo
        )
        vm.onNameChanged("Jaket Jeans")
        vm.onPhotoSelected("https://img/1.jpg", 0)
        vm.onPhotoSelected("https://img/2.jpg", 1)
        vm.onPhotoSelected("https://img/3.jpg", 2)

        vm.onRemovePhoto(0)
        assertEquals("https://img/2.jpg", vm.uiState.value.mainPhotoUri)
        assertEquals("https://img/3.jpg", vm.uiState.value.detailPhotoUri)
        assertNull(vm.uiState.value.extraPhotoUri)

        val slotOnlyRepo = FakeWardrobeRepo()
        val slotVm = AddEditClothingViewModel(
            savedStateHandle = SavedStateHandle(),
            authRepository = FakeAuthRepo(flowOf(10L), flowOf(sampleProfile())),
            wardrobeRepository = slotOnlyRepo
        )
        slotVm.onNameChanged("Kaos Polos")
        slotVm.onPhotoSelected("https://img/extra-only.jpg", 2)

        var saved = false
        slotVm.save { saved = true }
        assertTrue(saved)
        assertEquals(1, slotOnlyRepo.savedDrafts.size)
        assertEquals("https://img/extra-only.jpg", slotOnlyRepo.savedDrafts.first().mainPhotoUri)
        assertNull(slotOnlyRepo.savedDrafts.first().detailPhotoUri)
        assertNull(slotOnlyRepo.savedDrafts.first().extraPhotoUri)
    }

    @Test
    fun preferencesViewModel_loadingFallbackUserIdSizeSemuaAndRadiusClamp() = runTest {
        val gate = CompletableDeferred<UserProfile?>()
        val authRepo = FakeAuthRepo(
            userIdFlow = flowOf(42L),
            userProfileFlow = flow { emit(gate.await()) }
        )
        val profileRepo = FakeProfileReviewRepo()
        val vm = PreferencesViewModel(
            savedStateHandle = SavedStateHandle(),
            authRepository = authRepo,
            profileReviewRepository = profileRepo
        )

        assertTrue(vm.uiState.value.isLoading)
        gate.complete(null)
        assertFalse(vm.uiState.value.isLoading)

        vm.onSizeSelected("Semua")
        vm.onCustomRadiusTextChanged("25000")
        assertTrue("Semua" in (CloBleConstants.SIZES + "Semua"))

        var onSuccessCalled = false
        vm.savePreferences { onSuccessCalled = true }
        assertTrue(onSuccessCalled)
        assertEquals(1, profileRepo.updatedPreferences.size)
        val (savedUserId, savedPrefs) = profileRepo.updatedPreferences.first()
        assertEquals(42L, savedUserId)
        assertEquals("Semua", savedPrefs.preferredSize)
        assertEquals(20000, savedPrefs.preferredRadiusKm)

        val unauthenticatedVm = PreferencesViewModel(
            savedStateHandle = SavedStateHandle(),
            authRepository = FakeAuthRepo(flowOf(null), flowOf(null)),
            profileReviewRepository = FakeProfileReviewRepo()
        )
        var unauthSaved = false
        unauthenticatedVm.savePreferences { unauthSaved = true }
        assertFalse(unauthSaved)
        assertFalse(unauthenticatedVm.uiState.value.isSaving)
        assertNotNull(unauthenticatedVm.uiState.value.errorMessage)
    }

    @Test
    fun editProfileViewModel_usesLoggedInUserIdFallbackAndErrorsWhenNull() = runTest {
        val profileRepo = FakeProfileReviewRepo()
        val vm = EditProfileViewModel(
            authRepository = FakeAuthRepo(flowOf(77L), flowOf(null)),
            profileReviewRepository = profileRepo
        )
        vm.onNameChanged("Haqi Baru")

        var saved = false
        vm.saveProfile { saved = true }
        assertTrue(saved)
        assertEquals(1, profileRepo.updatedProfiles.size)
        assertEquals(77L, profileRepo.updatedProfiles.first().first)

        val noSessionVm = EditProfileViewModel(
            authRepository = FakeAuthRepo(flowOf(null), flowOf(null)),
            profileReviewRepository = FakeProfileReviewRepo()
        )
        noSessionVm.onNameChanged("Haqi")
        var noSessionSaved = false
        noSessionVm.saveProfile { noSessionSaved = true }
        assertFalse(noSessionSaved)
        assertFalse(noSessionVm.uiState.value.isSaving)
        assertNotNull(noSessionVm.uiState.value.errorMessage)
    }

    @Test
    fun myProfileViewModel_exposesErrorWhenNullAndRecoversOnRetry() = runTest {
        val profileFlow = MutableStateFlow<UserProfile?>(null)
        val vm = MyProfileViewModel(
            authRepository = FakeAuthRepo(flowOf(10L), profileFlow),
            profileReviewRepository = FakeProfileReviewRepo(),
            wardrobeRepository = FakeWardrobeRepo(),
            discoveryMatchRepository = FakeDiscoveryMatchRepo()
        )

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.userProfile)
        assertNotNull(vm.uiState.value.errorMessage)

        profileFlow.value = sampleProfile(10L)
        vm.retryLoadProfile()

        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.userProfile)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun addEditClothingViewModel_handlesLoadFailureMaxLengthsAndSessionFallback() = runTest {
        val failedEditVm = AddEditClothingViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to 99L)),
            authRepository = FakeAuthRepo(flowOf(10L), flowOf(sampleProfile())),
            wardrobeRepository = FakeWardrobeRepo(existing = null)
        )
        assertFalse(failedEditVm.uiState.value.isLoading)
        assertTrue(failedEditVm.uiState.value.isItemLoadFailed)
        assertNotNull(failedEditVm.uiState.value.errorMessage)
        failedEditVm.onNameChanged("Harus Ditolak")
        assertEquals("", failedEditVm.uiState.value.name)

        val fallbackRepo = FakeWardrobeRepo()
        val delayedUserIdFlow = MutableStateFlow<Long?>(null)
        val addVm = AddEditClothingViewModel(
            savedStateHandle = SavedStateHandle(),
            authRepository = FakeAuthRepo(delayedUserIdFlow, flowOf(sampleProfile())),
            wardrobeRepository = fallbackRepo
        )
        addVm.onNameChanged("A".repeat(121))
        var saved = false
        addVm.save { saved = true }
        assertFalse(saved)
        assertTrue(addVm.uiState.value.errorMessage.orEmpty().contains("120"))

        addVm.onNameChanged("Baju Valid")
        addVm.onDescriptionChanged("D".repeat(2001))
        addVm.save { saved = true }
        assertFalse(saved)
        assertTrue(addVm.uiState.value.errorMessage.orEmpty().contains("2000"))

        addVm.onDescriptionChanged("Deskripsi valid")
        addVm.save { saved = true }
        assertFalse(saved)
        assertFalse(addVm.uiState.value.isSaving)
        assertTrue(addVm.uiState.value.errorMessage.orEmpty().contains("Sesi pengguna"))

        delayedUserIdFlow.value = 88L
        addVm.save { saved = true }
        assertTrue(saved)
        assertEquals(1, fallbackRepo.savedDrafts.size)
    }

    @Test
    fun editProfileViewModel_abortsWhenAvatarUploadFailsAndRecoversOnRetry() = runTest {
        val profileFlow = MutableStateFlow<UserProfile?>(null)
        val failingUploadRepo = object : ProfileReviewRepository {
            val updates = mutableListOf<UserProfileUpdate>()
            override fun observeUserProfile(userId: Long, viewerUserId: Long?): Flow<UserProfile?> = flowOf(null)
            override fun observeUserReviews(userId: Long): Flow<List<UserReview>> = flowOf(emptyList())
            override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> = flowOf(false)
            override suspend fun updatePreferences(userId: Long, prefs: UserPreferences): Boolean = true
            override suspend fun updateBio(userId: Long, bio: String) {}
            override suspend fun updateProfile(userId: Long, update: UserProfileUpdate): Boolean {
                updates.add(update)
                return true
            }
            override suspend fun persistAvatarPhoto(sourceUriString: String): String? = null
            override suspend fun submitReview(matchId: Long, reviewerId: Long, rating: Int, comment: String): ReviewSubmitResult =
                ReviewSubmitResult.Success(1L)
        }

        val vm = EditProfileViewModel(
            authRepository = FakeAuthRepo(flowOf(10L), profileFlow),
            profileReviewRepository = failingUploadRepo
        )
        assertTrue(vm.uiState.value.isProfileLoadFailed)

        profileFlow.value = sampleProfile(10L).copy(avatarUri = "content://local/avatar.jpg")
        vm.retryLoadProfile()
        assertFalse(vm.uiState.value.isProfileLoadFailed)
        assertEquals("Haqi", vm.uiState.value.name)

        var saved = false
        vm.saveProfile { saved = true }
        assertFalse(saved)
        assertEquals(0, failingUploadRepo.updates.size)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("foto profil"))

        vm.onNameChanged("N".repeat(101))
        vm.saveProfile { saved = true }
        assertFalse(saved)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("100"))
    }

    @Test
    fun preferencesViewModel_flagsLoadFailureForNonOnboardingAndValidatesCityLength() = runTest {
        val profileFlow = MutableStateFlow<UserProfile?>(null)
        val vm = PreferencesViewModel(
            savedStateHandle = SavedStateHandle(mapOf("isOnboarding" to false)),
            authRepository = FakeAuthRepo(flowOf(10L), profileFlow),
            profileReviewRepository = FakeProfileReviewRepo()
        )
        assertTrue(vm.uiState.value.isProfileLoadFailed)

        profileFlow.value = sampleProfile(10L)
        vm.retryLoadPreferences()
        assertFalse(vm.uiState.value.isProfileLoadFailed)
        assertEquals("Bandung, Jawa Barat", vm.uiState.value.selectedCity)

        vm.onLocationSelected("K".repeat(121), -6.9175, 107.6191)
        var saved = false
        vm.savePreferences { saved = true }
        assertFalse(saved)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("120"))
    }

    @Test
    fun myProfileViewModel_clearsPasswordsOnDismissAndSuppressesErrorOnLogout() = runTest {
        val profileFlow = MutableStateFlow<UserProfile?>(sampleProfile(10L))
        var logoutAction: (() -> Unit)? = null
        val authRepo = object : AuthRepository {
            override fun observeLoggedInUserId(): Flow<Long?> = flowOf(10L)
            override fun observeCurrentUser(): Flow<UserProfile?> = profileFlow
            override suspend fun login(email: String, rawPassword: String): AuthResult = AuthResult.Success(10L)
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
            ): AuthResult = AuthResult.Success(10L)
            override suspend fun logout() {
                logoutAction?.invoke()
            }
        }
        val vm = MyProfileViewModel(
            authRepository = authRepo,
            profileReviewRepository = FakeProfileReviewRepo(),
            wardrobeRepository = FakeWardrobeRepo(),
            discoveryMatchRepository = FakeDiscoveryMatchRepo()
        )

        vm.openChangePasswordDialog()
        vm.onCurrentPasswordChanged("Old12345")
        vm.onNewPasswordChanged("New12345")
        vm.onConfirmPasswordChanged("New12345")
        vm.dismissChangePasswordDialog()
        assertEquals("", vm.uiState.value.currentPassword)
        assertEquals("", vm.uiState.value.newPassword)
        assertEquals("", vm.uiState.value.confirmPassword)

        logoutAction = { profileFlow.value = null }
        var loggedOut = false
        vm.logout { loggedOut = true }
        assertTrue(loggedOut)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun rateUserViewModel_waitsForBothMatchDetailAndReviewStatus() = runTest {
        val reviewGate = CompletableDeferred<Boolean>()
        val detail = VerifiedMatchDetail(
            matchId = 50L,
            status = MatchStatus.COMPLETED,
            meetingLocation = null,
            createdAt = 100L,
            updatedAt = 200L,
            myUserId = 10L,
            partnerUserId = 20L,
            partnerName = "Partner",
            partnerCity = "Bandung",
            partnerAvatarUri = null,
            partnerAverageRating = 4.5,
            partnerReviewCount = 2,
            myItem = sampleItem(1L),
            partnerItem = sampleItem(2L)
        )
        val discoveryRepo = object : DiscoveryMatchRepository {
            override fun observeDiscoverCandidates(viewerUserId: Long, filter: DiscoverFilter): Flow<List<DiscoverCandidate>> =
                flowOf(emptyList())
            override fun observeMatchDetail(matchId: Long, viewerUserId: Long): Flow<VerifiedMatchDetail?> =
                flowOf(detail)
            override fun observeNotifications(userId: Long): Flow<List<NotificationFeedItem>> = flowOf(emptyList())
            override fun observeUnreadNotificationCount(userId: Long): Flow<Int> = flowOf(0)
            override fun observeOutgoingInterests(userId: Long): Flow<List<OutgoingInterestSummary>> = flowOf(emptyList())
            override fun observePassedClothing(userId: Long): Flow<List<PassedClothingSummary>> = flowOf(emptyList())
            override suspend fun passCandidate(userId: Long, targetItemId: Long): Boolean = true
            override suspend fun sendInterest(senderId: Long, targetItemId: Long): SendInterestResult = SendInterestResult.Success(1L)
            override suspend fun cancelOutgoingInterest(userId: Long, interestId: Long): CancelInterestResult = CancelInterestResult.Success
            override suspend fun convertPassToInterest(userId: Long, targetItemId: Long): SendInterestResult = SendInterestResult.Success(1L)
            override suspend fun restorePassToDiscover(userId: Long, targetItemId: Long): Boolean = true
            override suspend fun respondToInterestAndCreateMatch(actorUserId: Long, interestId: Long, chosenSenderItemId: Long): RespondInterestResult =
                RespondInterestResult.Success(1L)
            override suspend fun declineIncomingInterest(actorUserId: Long, interestId: Long): DeclineInterestResult = DeclineInterestResult.Success
            override suspend fun cancelMatch(matchId: Long, actorUserId: Long): CancelMatchResult = CancelMatchResult.Success
            override suspend fun completeMatchExchange(matchId: Long, actorUserId: Long, meetingLocation: String?): CompleteMatchResult =
                CompleteMatchResult.Success
            override suspend fun markNotificationsRead(userId: Long) {}
            override suspend fun markNotificationReadById(notificationId: Long) {}
        }
        val profileRepo = object : ProfileReviewRepository {
            override fun observeUserProfile(userId: Long, viewerUserId: Long?): Flow<UserProfile?> = flowOf(null)
            override fun observeUserReviews(userId: Long): Flow<List<UserReview>> = flowOf(emptyList())
            override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> = flow { emit(reviewGate.await()) }
            override suspend fun updatePreferences(userId: Long, prefs: UserPreferences): Boolean = true
            override suspend fun updateBio(userId: Long, bio: String) {}
            override suspend fun updateProfile(userId: Long, update: UserProfileUpdate): Boolean = true
            override suspend fun persistAvatarPhoto(sourceUriString: String): String? = null
            override suspend fun submitReview(matchId: Long, reviewerId: Long, rating: Int, comment: String): ReviewSubmitResult =
                ReviewSubmitResult.Success(1L)
        }

        val vm = RateUserViewModel(
            savedStateHandle = SavedStateHandle(mapOf("matchId" to 50L)),
            authRepository = FakeAuthRepo(flowOf(10L), flowOf(sampleProfile())),
            discoveryMatchRepository = discoveryRepo,
            profileReviewRepository = profileRepo
        )

        assertNotNull(vm.uiState.value.matchDetail)
        assertTrue(vm.uiState.value.isLoading)

        reviewGate.complete(true)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.alreadyReviewed)
    }

    @Test
    fun wardrobeViewModel_surfacesDatabaseErrorMessages() = runTest {
        val item = sampleItem(77L)
        val wardrobeRepo = object : WardrobeRepository {
            override fun observeMyWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> =
                flowOf(listOf(WardrobeItemSummary(item = item, isInActiveMatch = false)))
            override fun observeUserPublicWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
            override fun observeClothingItem(itemId: Long): Flow<ClothingItem?> = flowOf(item)
            override suspend fun getClothingItem(itemId: Long): ClothingItem? = item
            override suspend fun isItemLocked(itemId: Long): Boolean = false
            override suspend fun addClothingItem(ownerId: Long, draft: ClothingDraft): WardrobeMutationResult =
                WardrobeMutationResult.Success(1L)
            override suspend fun updateClothingItem(actorUserId: Long, itemId: Long, draft: ClothingDraft): WardrobeMutationResult =
                WardrobeMutationResult.Success(itemId)
            override suspend fun setBarterAllowed(actorUserId: Long, itemId: Long, allowed: Boolean): WardrobeMutationResult =
                WardrobeMutationResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Server menolak izin barter"))
            override suspend fun softDeleteClothingItem(actorUserId: Long, itemId: Long): WardrobeMutationResult =
                WardrobeMutationResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Server gagal menghapus"))
            override suspend fun persistLocalPhoto(sourceUriString: String): String? = null
            override suspend fun countEligibleBarterItems(ownerId: Long): Int = 1
        }
        val vm = WardrobeViewModel(
            authRepository = FakeAuthRepo(flowOf(10L), flowOf(sampleProfile())),
            wardrobeRepository = wardrobeRepo,
            discoveryMatchRepository = FakeDiscoveryMatchRepo()
        )

        vm.toggleBarterAllowed(77L, false)
        assertEquals("Server menolak izin barter", vm.uiState.value.errorMessage)

        vm.requestDelete(item)
        vm.confirmDelete()
        assertEquals("Server gagal menghapus", vm.uiState.value.errorMessage)
    }
}
