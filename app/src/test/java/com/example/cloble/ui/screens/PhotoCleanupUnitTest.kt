package com.example.cloble.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.cloble.data.remote.extractCleanupPhotoUris
import com.example.cloble.domain.model.ClothingDraft
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.UserPreferences
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.model.UserProfileUpdate
import com.example.cloble.domain.model.UserReview
import com.example.cloble.domain.model.WardrobeItemSummary
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.result.ChangePasswordResult
import com.example.cloble.domain.result.RequestPasswordResetOtpResult
import com.example.cloble.domain.result.ResetPasswordResult
import com.example.cloble.domain.result.ReviewSubmitResult
import com.example.cloble.domain.result.WardrobeMutationResult
import com.example.cloble.ui.screens.profile.EditProfileViewModel
import com.example.cloble.ui.screens.wardrobe.AddEditClothingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoCleanupUnitTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleProfile(): UserProfile = UserProfile(
        id = 10L,
        name = "Bagas",
        email = "bagas@example.com",
        bio = "Bio",
        avatarUri = "https://cdn.example/10/existing_avatar.jpg",
        city = "Kota Bandung",
        latitude = -6.9175,
        longitude = 107.6191,
        preferredSize = "M",
        preferredGender = "Uniseks",
        preferredRadiusKm = 10,
        createdAt = 1000L
    )

    private fun sampleClothingItem(): ClothingItem = ClothingItem(
        id = 55L,
        ownerId = 10L,
        name = "Jaket Denim",
        description = "Masih bagus",
        category = "Luaran",
        size = "M",
        gender = "Uniseks",
        color = "Biru",
        condition = "Baik",
        mainPhotoUri = "https://cdn.example/10/existing_main.jpg",
        detailPhotoUri = "https://cdn.example/10/existing_detail.jpg",
        extraPhotoUri = null,
        isBarterAllowed = true,
        status = ItemStatus.AVAILABLE,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private class FakeAuthRepo(private val profile: UserProfile) : AuthRepository {
        override fun observeLoggedInUserId(): Flow<Long?> = flowOf(profile.id)
        override fun observeCurrentUser(): Flow<UserProfile?> = flowOf(profile)
        override suspend fun login(email: String, rawPassword: String): AuthResult = AuthResult.InvalidCredentials
        override suspend fun register(
            name: String,
            email: String,
            rawPassword: String,
            city: String,
            latitude: Double?,
            longitude: Double?
        ): AuthResult = AuthResult.InvalidCredentials
        override suspend fun requestPasswordResetOtp(email: String): RequestPasswordResetOtpResult =
            RequestPasswordResetOtpResult.Success
        override suspend fun verifyOtpAndResetPassword(
            email: String,
            otpCode: String,
            newRawPassword: String
        ): ResetPasswordResult = ResetPasswordResult.Success
        override suspend fun changePassword(currentRawPassword: String, newRawPassword: String): ChangePasswordResult =
            ChangePasswordResult.Success
        override suspend fun validateSession(): Boolean = true
        override suspend fun logout() {}
    }

    private class RecordingWardrobeRepo(
        private val existing: ClothingItem? = null
    ) : WardrobeRepository {
        val deletedBatches = mutableListOf<List<String>>()
        private var counter = 0

        override fun observeMyWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeUserPublicWardrobe(ownerId: Long): Flow<List<WardrobeItemSummary>> = flowOf(emptyList())
        override fun observeClothingItem(itemId: Long): Flow<ClothingItem?> = flowOf(existing)
        override suspend fun getClothingItem(itemId: Long): ClothingItem? = existing
        override suspend fun isItemLocked(itemId: Long): Boolean = false
        override suspend fun addClothingItem(ownerId: Long, draft: ClothingDraft): WardrobeMutationResult =
            WardrobeMutationResult.Success(1L)
        override suspend fun updateClothingItem(actorUserId: Long, itemId: Long, draft: ClothingDraft): WardrobeMutationResult =
            WardrobeMutationResult.Success(itemId)
        override suspend fun setBarterAllowed(actorUserId: Long, itemId: Long, allowed: Boolean): WardrobeMutationResult =
            WardrobeMutationResult.Success(itemId)
        override suspend fun softDeleteClothingItem(actorUserId: Long, itemId: Long): WardrobeMutationResult =
            WardrobeMutationResult.Success(itemId)
        override suspend fun persistLocalPhoto(sourceUriString: String): String {
            counter += 1
            return "https://cdn.example/10/draft_$counter.jpg"
        }
        override suspend fun deleteUploadedPhotos(photoUris: List<String>) {
            deletedBatches.add(photoUris)
        }
        override suspend fun countEligibleBarterItems(ownerId: Long): Int = 0
    }

    private class RecordingProfileRepo : ProfileReviewRepository {
        val deletedBatches = mutableListOf<List<String>>()
        private var counter = 0

        override fun observeUserProfile(userId: Long, viewerUserId: Long?): Flow<UserProfile?> = flowOf(null)
        override fun observeUserReviews(userId: Long): Flow<List<UserReview>> = flowOf(emptyList())
        override fun hasUserReviewedMatch(matchId: Long, reviewerId: Long): Flow<Boolean> = flowOf(false)
        override suspend fun updatePreferences(userId: Long, prefs: UserPreferences): Boolean = true
        override suspend fun updateBio(userId: Long, bio: String) {}
        override suspend fun updateProfile(userId: Long, update: UserProfileUpdate): Boolean = true
        override suspend fun persistAvatarPhoto(sourceUriString: String): String {
            counter += 1
            return "https://cdn.example/10/avatar_draft_$counter.jpg"
        }
        override suspend fun deleteUploadedPhotos(photoUris: List<String>) {
            deletedBatches.add(photoUris)
        }
        override suspend fun submitReview(matchId: Long, reviewerId: Long, rating: Int, comment: String): ReviewSubmitResult =
            ReviewSubmitResult.Success(1L)
    }

    @Test
    fun addEditClothingViewModel_cleansUpDiscardedDraft_whenViewModelCleared() = runTest(testDispatcher) {
        val repo = RecordingWardrobeRepo()
        val vm = AddEditClothingViewModel(
            savedStateHandle = SavedStateHandle(),
            authRepository = FakeAuthRepo(sampleProfile()),
            wardrobeRepository = repo,
            cleanupScope = this
        )
        vm.onPhotoSelected("content://gallery/photo1.jpg", 0)
        assertEquals("https://cdn.example/10/draft_1.jpg", vm.uiState.value.mainPhotoUri)
        assertTrue(repo.deletedBatches.isEmpty())

        val store = ViewModelStore()
        store.put("vm", vm)
        store.clear()

        assertEquals(listOf(listOf("https://cdn.example/10/draft_1.jpg")), repo.deletedBatches)
    }

    @Test
    fun addEditClothingViewModel_cleansOnlyReplacedDraftOnSave_andNeverDeletesExistingDbPhotosOnDiscard() = runTest(testDispatcher) {
        val repo = RecordingWardrobeRepo()
        val addVm = AddEditClothingViewModel(
            savedStateHandle = SavedStateHandle(),
            authRepository = FakeAuthRepo(sampleProfile()),
            wardrobeRepository = repo,
            cleanupScope = this
        )
        addVm.onNameChanged("Kemeja Flanel")
        addVm.onPhotoSelected("content://gallery/first.jpg", 0)
        addVm.onPhotoSelected("content://gallery/second.jpg", 0)

        var saved = false
        addVm.save { saved = true }
        assertTrue(saved)
        assertEquals(listOf(listOf("https://cdn.example/10/draft_1.jpg")), repo.deletedBatches)

        val store = ViewModelStore()
        store.put("add", addVm)
        store.clear()
        assertEquals(1, repo.deletedBatches.size)

        val editRepo = RecordingWardrobeRepo(existing = sampleClothingItem())
        val editVm = AddEditClothingViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to 55L)),
            authRepository = FakeAuthRepo(sampleProfile()),
            wardrobeRepository = editRepo,
            cleanupScope = this
        )
        editVm.onRemovePhoto(0)
        val editStore = ViewModelStore()
        editStore.put("edit", editVm)
        editStore.clear()
        assertTrue(editRepo.deletedBatches.isEmpty())
    }

    @Test
    fun editProfileViewModel_cleansUpDiscardedAndReplacedDraftAvatars_onlyForNewUploads() = runTest(testDispatcher) {
        val repo = RecordingProfileRepo()
        val vm = EditProfileViewModel(
            authRepository = FakeAuthRepo(sampleProfile()),
            profileReviewRepository = repo,
            cleanupScope = this
        )
        vm.onAvatarPicked("content://gallery/avatar1.jpg")
        vm.onAvatarPicked("content://gallery/avatar2.jpg")

        var saved = false
        vm.saveProfile { saved = true }
        assertTrue(saved)
        assertEquals(listOf(listOf("https://cdn.example/10/avatar_draft_1.jpg")), repo.deletedBatches)

        val discardRepo = RecordingProfileRepo()
        val discardVm = EditProfileViewModel(
            authRepository = FakeAuthRepo(sampleProfile()),
            profileReviewRepository = discardRepo,
            cleanupScope = this
        )
        discardVm.onRemoveAvatar()
        val store = ViewModelStore()
        store.put("discard", discardVm)
        store.clear()
        assertTrue(discardRepo.deletedBatches.isEmpty())

        val discardVm2 = EditProfileViewModel(
            authRepository = FakeAuthRepo(sampleProfile()),
            profileReviewRepository = discardRepo,
            cleanupScope = this
        )
        discardVm2.onAvatarPicked("content://gallery/avatar3.jpg")
        val store2 = ViewModelStore()
        store2.put("discard2", discardVm2)
        store2.clear()
        assertEquals(listOf(listOf("https://cdn.example/10/avatar_draft_1.jpg")), discardRepo.deletedBatches)
    }

    @Test
    fun extractCleanupPhotoUris_ignoresNullBlankAndDuplicates() {
        val json = JSONObject().put(
            "cleanupPhotoUris",
            JSONArray(listOf("  ", "null", "https://cdn.example/10/a.jpg", "https://cdn.example/10/a.jpg", "https://cdn.example/10/b.jpg"))
        )
        assertEquals(
            listOf("https://cdn.example/10/a.jpg", "https://cdn.example/10/b.jpg"),
            json.extractCleanupPhotoUris()
        )
    }
}
