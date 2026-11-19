package com.example.cloble.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.CityPreset
import com.example.cloble.domain.model.UserProfileUpdate
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val errorMessage: String? = null
)

class EditProfileViewModel(
    private val authRepository: AuthRepository,
    private val profileReviewRepository: ProfileReviewRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private var hasInitialized = false

    init {
        viewModelScope.launch {
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
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onNameChanged(newName: String) {
        _uiState.update { it.copy(name = newName, errorMessage = null) }
    }

    fun onBioChanged(newBio: String) {
        _uiState.update { it.copy(bio = newBio, errorMessage = null) }
    }

    fun onAvatarPicked(sourceUriString: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null) }
            val persisted = profileReviewRepository.persistAvatarPhoto(sourceUriString)
            if (persisted != null) {
                _uiState.update { it.copy(avatarUri = persisted, isUploadingAvatar = false) }
            } else {
                _uiState.update {
                    it.copy(
                        isUploadingAvatar = false,
                        errorMessage = "Gagal memproses foto profil. Coba pilih foto lain."
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
        _uiState.update { it.copy(preferredSize = size) }
    }

    fun onPreferredGenderSelected(gender: String) {
        _uiState.update { it.copy(preferredGender = gender) }
    }

    fun onPreferredRadiusSelected(radiusKm: Int) {
        _uiState.update { it.copy(preferredRadiusKm = radiusKm) }
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val state = _uiState.value
        val userId = state.userId ?: return
        val cleanName = state.name.trim()
        val cleanCity = state.city.trim()

        if (cleanName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Nama lengkap tidak boleh kosong.") }
            return
        }
        if (cleanCity.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Lokasi tidak boleh kosong.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val finalAvatar = state.avatarUri?.let { uri ->
                if (uri.startsWith("http://") || uri.startsWith("https://") || uri.startsWith("/")) {
                    uri
                } else {
                    profileReviewRepository.persistAvatarPhoto(uri)
                }
            }
            val success = profileReviewRepository.updateProfile(
                userId = userId,
                update = UserProfileUpdate(
                    name = cleanName,
                    bio = state.bio.trim(),
                    avatarUri = finalAvatar,
                    city = cleanCity,
                    latitude = state.latitude,
                    longitude = state.longitude,
                    preferredSize = state.preferredSize,
                    preferredGender = state.preferredGender,
                    preferredRadiusKm = state.preferredRadiusKm
                )
            )
            if (success) {
                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } else {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Gagal menyimpan perubahan profil. Periksa koneksi internet Anda."
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication
                EditProfileViewModel(
                    authRepository = app.container.authRepository,
                    profileReviewRepository = app.container.profileReviewRepository
                )
            }
        }
    }
}
