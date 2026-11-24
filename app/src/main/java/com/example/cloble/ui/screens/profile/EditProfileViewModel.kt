package com.example.cloble.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.data.remote.SupabaseProfileReviewRepository
import com.example.cloble.domain.model.CityPreset
import com.example.cloble.domain.model.UserProfileUpdate
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val PRESET_RADII_KM = setOf(5, 10, 25, 50, 100)

data class EditProfileUiState(
    val userId: Long? = null,
    val name: String = "",
    val bio: String = "",
    val avatarUri: String? = null,
    val city: String = CityPreset.BANDUNG.displayName,
    val latitude: Double = CityPreset.BANDUNG.latitude,
    val longitude: Double = CityPreset.BANDUNG.longitude,
    val preferredSize: String = "M",
    val preferredGender: String = "Uniseks",
    val preferredRadiusKm: Int = 10,
    val isCustomRadius: Boolean = false,
    val customRadiusText: String = "10",
    val isProfileLoadFailed: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val errorMessage: String? = null,
    val errorScrollTrigger: Int = 0
)

class EditProfileViewModel(
    private val authRepository: AuthRepository,
    private val profileReviewRepository: ProfileReviewRepository,
    private val cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private var hasInitialized = false
    private var loadJob: Job? = null
    private val uploadedDraftAvatarUris = LinkedHashSet<String>()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val user = authRepository.observeCurrentUser().firstOrNull()
            if (user != null && !hasInitialized) {
                hasInitialized = true
                _uiState.update {
                    it.copy(
                        userId = user.id,
                        name = user.name,
                        bio = user.bio,
                        avatarUri = user.avatarUri,
                        city = user.city,
                        latitude = user.latitude,
                        longitude = user.longitude,
                        preferredSize = user.preferredSize,
                        preferredGender = user.preferredGender,
                        preferredRadiusKm = user.preferredRadiusKm,
                        isCustomRadius = user.preferredRadiusKm !in PRESET_RADII_KM,
                        customRadiusText = user.preferredRadiusKm.toString(),
                        isProfileLoadFailed = false,
                        isLoading = false
                    )
                }
            } else {
                val fallbackUserId = user?.id ?: authRepository.observeLoggedInUserId().firstOrNull()
                _uiState.update {
                    it.copy(
                        userId = it.userId ?: fallbackUserId,
                        isProfileLoadFailed = user == null && !hasInitialized,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun retryLoadProfile() {
        hasInitialized = false
        _uiState.update { it.copy(isLoading = true, isProfileLoadFailed = false, errorMessage = null) }
        loadProfile()
    }

    fun onNameChanged(newName: String) {
        _uiState.update { it.copy(name = newName, errorMessage = null) }
    }

    fun onBioChanged(newBio: String) {
        _uiState.update { it.copy(bio = newBio, errorMessage = null) }
    }

    fun onAvatarPicked(sourceUriString: String) {
        val current = _uiState.value
        if (current.isUploadingAvatar || current.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null) }
            val isRemoteInput = sourceUriString.startsWith("http://") || sourceUriString.startsWith("https://")
            val persisted = profileReviewRepository.persistAvatarPhoto(sourceUriString)
            if (persisted != null) {
                if (!isRemoteInput) {
                    synchronized(uploadedDraftAvatarUris) {
                        uploadedDraftAvatarUris.add(persisted)
                    }
                }
                _uiState.update { it.copy(avatarUri = persisted, isUploadingAvatar = false) }
            } else {
                _uiState.update {
                    it.copy(
                        isUploadingAvatar = false,
                        errorMessage = "Gagal memproses foto profil. Pastikan format JPG/PNG/WebP dan ukuran maksimal 5 MB.",
                        errorScrollTrigger = it.errorScrollTrigger + 1
                    )
                }
            }
        }
    }

    fun onRemoveAvatar() {
        _uiState.update { it.copy(avatarUri = null, errorMessage = null) }
    }

    fun onLocationSelected(city: String, latitude: Double, longitude: Double) {
        _uiState.update {
            it.copy(
                city = city,
                latitude = latitude,
                longitude = longitude,
                errorMessage = null
            )
        }
    }

    fun onPreferredSizeSelected(size: String) {
        _uiState.update { it.copy(preferredSize = size, errorMessage = null) }
    }

    fun onPreferredGenderSelected(gender: String) {
        _uiState.update { it.copy(preferredGender = gender, errorMessage = null) }
    }

    fun onPreferredRadiusSelected(radiusKm: Int) {
        _uiState.update {
            it.copy(
                preferredRadiusKm = radiusKm,
                isCustomRadius = radiusKm !in PRESET_RADII_KM,
                customRadiusText = if (radiusKm > 0) radiusKm.toString() else "",
                errorMessage = null
            )
        }
    }

    fun onCustomRadiusModeSelected() {
        _uiState.update {
            it.copy(
                isCustomRadius = true,
                customRadiusText = if (it.customRadiusText.isBlank() && it.preferredRadiusKm > 0) {
                    it.preferredRadiusKm.toString()
                } else {
                    it.customRadiusText
                },
                errorMessage = null
            )
        }
    }

    fun onCustomRadiusTextChanged(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(5)
        val parsed = digits.toIntOrNull()
        _uiState.update {
            it.copy(
                isCustomRadius = true,
                customRadiusText = digits,
                preferredRadiusKm = parsed ?: 0,
                errorMessage = null
            )
        }
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isSaving || state.isUploadingAvatar) return
        if (state.isProfileLoadFailed) {
            _uiState.update {
                it.copy(
                    errorMessage = "Data profil lama belum termuat. Ketuk Muat Ulang sebelum menyimpan agar data tidak tertimpa.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        val cleanName = state.name.trim()
        val cleanBio = state.bio.trim()
        val cleanCity = state.city.trim()

        if (cleanName.isEmpty()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Nama lengkap tidak boleh kosong.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        if (cleanName.length > 100) {
            _uiState.update {
                it.copy(
                    errorMessage = "Nama lengkap maksimal 100 karakter.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        if (cleanBio.length > 1000) {
            _uiState.update {
                it.copy(
                    errorMessage = "Bio maksimal 1000 karakter.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        if (cleanCity.isEmpty()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Lokasi tidak boleh kosong.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        if (cleanCity.length > 120) {
            _uiState.update {
                it.copy(
                    errorMessage = "Nama lokasi maksimal 120 karakter.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }

        val rawRadius = if (state.isCustomRadius) {
            state.customRadiusText.toIntOrNull() ?: 0
        } else {
            state.preferredRadiusKm
        }
        if (rawRadius <= 0) {
            _uiState.update {
                it.copy(
                    errorMessage = "Radius pencarian harus diisi lebih dari 0 km.",
                    errorScrollTrigger = it.errorScrollTrigger + 1
                )
            }
            return
        }
        val effectiveRadius = rawRadius.coerceAtMost(20000)

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val userId = state.userId ?: authRepository.observeLoggedInUserId().firstOrNull()
            if (userId == null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Sesi pengguna tidak ditemukan. Silakan masuk kembali.",
                        errorScrollTrigger = it.errorScrollTrigger + 1
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(userId = userId) }
            val rawAvatar = state.avatarUri
            val finalAvatar = if (
                rawAvatar == null ||
                rawAvatar.startsWith("http://") ||
                rawAvatar.startsWith("https://") ||
                rawAvatar.startsWith("/")
            ) {
                rawAvatar
            } else {
                val uploadedAvatar = profileReviewRepository.persistAvatarPhoto(rawAvatar)
                if (uploadedAvatar == null) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "Gagal mengunggah foto profil. Silakan pilih ulang foto dan coba lagi.",
                            errorScrollTrigger = it.errorScrollTrigger + 1
                        )
                    }
                    return@launch
                }
                synchronized(uploadedDraftAvatarUris) {
                    uploadedDraftAvatarUris.add(uploadedAvatar)
                }
                _uiState.update { it.copy(avatarUri = uploadedAvatar) }
                uploadedAvatar
            }
            val success = profileReviewRepository.updateProfile(
                userId = userId,
                update = UserProfileUpdate(
                    name = cleanName,
                    bio = cleanBio,
                    avatarUri = finalAvatar,
                    city = cleanCity,
                    latitude = state.latitude,
                    longitude = state.longitude,
                    preferredSize = state.preferredSize,
                    preferredGender = state.preferredGender,
                    preferredRadiusKm = effectiveRadius
                )
            )
            if (success) {
                val unusedDrafts = synchronized(uploadedDraftAvatarUris) {
                    val stale = uploadedDraftAvatarUris.filter { it != finalAvatar }
                    uploadedDraftAvatarUris.clear()
                    stale
                }
                if (unusedDrafts.isNotEmpty()) {
                    cleanupScope.launch {
                        profileReviewRepository.deleteUploadedPhotos(unusedDrafts)
                    }
                }
                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } else {
                val serverError = (profileReviewRepository as? SupabaseProfileReviewRepository)
                    ?.consumeLastMutationError()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = serverError
                            ?: "Gagal menyimpan perubahan profil. Periksa koneksi internet Anda.",
                        errorScrollTrigger = it.errorScrollTrigger + 1
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val staleDrafts = synchronized(uploadedDraftAvatarUris) {
            val copy = uploadedDraftAvatarUris.toList()
            uploadedDraftAvatarUris.clear()
            copy
        }
        if (staleDrafts.isNotEmpty()) {
            cleanupScope.launch {
                profileReviewRepository.deleteUploadedPhotos(staleDrafts)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication
                EditProfileViewModel(
                    authRepository = app.container.authRepository,
                    profileReviewRepository = app.container.profileReviewRepository,
                    cleanupScope = app.container.cleanupScope
                )
            }
        }
    }
}
