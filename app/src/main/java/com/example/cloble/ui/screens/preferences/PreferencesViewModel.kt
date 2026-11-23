package com.example.cloble.ui.screens.preferences

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.data.remote.SupabaseProfileReviewRepository
import com.example.cloble.domain.model.CityPreset
import com.example.cloble.domain.model.UserPreferences
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val PRESET_RADII_KM = setOf(5, 10, 25, 50, 100)

data class PreferencesUiState(
    val isOnboarding: Boolean = false,
    val selectedCity: String = CityPreset.BANDUNG.displayName,
    val selectedLatitude: Double = CityPreset.BANDUNG.latitude,
    val selectedLongitude: Double = CityPreset.BANDUNG.longitude,
    val selectedRadiusKm: Int = 10,
    val isCustomRadius: Boolean = false,
    val customRadiusText: String = "10",
    val selectedSize: String = "M",
    val selectedGender: String = "Uniseks",
    val isProfileLoadFailed: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSavedSuccess: Boolean = false,
    val errorMessage: String? = null,
    val currentUserId: Long? = null
)

class PreferencesViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val profileReviewRepository: ProfileReviewRepository
) : ViewModel() {

    private val isOnboarding: Boolean = savedStateHandle.get<Boolean>("isOnboarding") ?: false

    private val _uiState = MutableStateFlow(PreferencesUiState(isOnboarding = isOnboarding))
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    private var hasUserEdited = false
    private var loadJob: Job? = null

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val user = authRepository.observeCurrentUser().firstOrNull()
            val fallbackUserId = user?.id ?: authRepository.observeLoggedInUserId().firstOrNull()
            _uiState.update { current ->
                if (user != null && !hasUserEdited) {
                    current.copy(
                        currentUserId = user.id,
                        selectedCity = user.city,
                        selectedLatitude = user.latitude,
                        selectedLongitude = user.longitude,
                        selectedRadiusKm = user.preferredRadiusKm,
                        isCustomRadius = user.preferredRadiusKm !in PRESET_RADII_KM,
                        customRadiusText = user.preferredRadiusKm.toString(),
                        selectedSize = user.preferredSize,
                        selectedGender = user.preferredGender,
                        isProfileLoadFailed = false,
                        isLoading = false
                    )
                } else {
                    current.copy(
                        currentUserId = current.currentUserId ?: fallbackUserId,
                        isProfileLoadFailed = !isOnboarding && user == null && !hasUserEdited,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun retryLoadPreferences() {
        hasUserEdited = false
        _uiState.update { it.copy(isLoading = true, isProfileLoadFailed = false, errorMessage = null) }
        loadPreferences()
    }

    fun onLocationSelected(city: String, latitude: Double, longitude: Double) {
        hasUserEdited = true
        _uiState.update {
            it.copy(
                selectedCity = city,
                selectedLatitude = latitude,
                selectedLongitude = longitude,
                errorMessage = null
            )
        }
    }

    fun onRadiusSelected(radiusKm: Int) {
        hasUserEdited = true
        _uiState.update {
            it.copy(
                selectedRadiusKm = radiusKm,
                isCustomRadius = radiusKm !in PRESET_RADII_KM,
                customRadiusText = if (radiusKm > 0) radiusKm.toString() else "",
                errorMessage = null
            )
        }
    }

    fun onCustomRadiusModeSelected() {
        hasUserEdited = true
        _uiState.update {
            it.copy(
                isCustomRadius = true,
                customRadiusText = if (it.customRadiusText.isBlank() && it.selectedRadiusKm > 0) {
                    it.selectedRadiusKm.toString()
                } else {
                    it.customRadiusText
                },
                errorMessage = null
            )
        }
    }

    fun onCustomRadiusTextChanged(raw: String) {
        hasUserEdited = true
        val digits = raw.filter { it.isDigit() }.take(5)
        val parsed = digits.toIntOrNull()
        _uiState.update {
            it.copy(
                isCustomRadius = true,
                customRadiusText = digits,
                selectedRadiusKm = parsed ?: 0,
                errorMessage = null
            )
        }
    }

    fun onSizeSelected(size: String) {
        hasUserEdited = true
        _uiState.update { it.copy(selectedSize = size, errorMessage = null) }
    }

    fun onGenderSelected(gender: String) {
        hasUserEdited = true
        _uiState.update { it.copy(selectedGender = gender, errorMessage = null) }
    }

    fun savePreferences(onSuccess: () -> Unit) {
        val s = _uiState.value
        if (s.isSaving) return
        val cleanCity = s.selectedCity.trim()

        if (cleanCity.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Lokasi tidak boleh kosong.") }
            return
        }
        if (cleanCity.length > 120) {
            _uiState.update { it.copy(errorMessage = "Nama lokasi maksimal 120 karakter.") }
            return
        }

        val rawRadius = if (s.isCustomRadius) {
            s.customRadiusText.toIntOrNull() ?: 0
        } else {
            s.selectedRadiusKm
        }
        if (rawRadius <= 0) {
            _uiState.update {
                it.copy(errorMessage = "Radius pencarian harus diisi lebih dari 0 km.")
            }
            return
        }
        val effectiveRadius = rawRadius.coerceAtMost(20000)

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val userId = s.currentUserId ?: authRepository.observeLoggedInUserId().firstOrNull()
            if (userId == null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSavedSuccess = false,
                        errorMessage = "Sesi pengguna tidak ditemukan. Silakan masuk kembali."
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(currentUserId = userId) }
            val prefs = UserPreferences(
                preferredSize = s.selectedSize,
                preferredGender = s.selectedGender,
                city = cleanCity,
                latitude = s.selectedLatitude,
                longitude = s.selectedLongitude,
                preferredRadiusKm = effectiveRadius
            )
            val success = profileReviewRepository.updatePreferences(userId, prefs)
            if (success) {
                _uiState.update { it.copy(isSaving = false, isSavedSuccess = true, errorMessage = null) }
                onSuccess()
            } else {
                val serverError = (profileReviewRepository as? SupabaseProfileReviewRepository)
                    ?.consumeLastMutationError()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSavedSuccess = false,
                        errorMessage = serverError
                            ?: "Gagal menyimpan preferensi. Periksa koneksi internet Anda dan coba lagi."
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication)
                val savedStateHandle = createSavedStateHandle()
                PreferencesViewModel(
                    savedStateHandle = savedStateHandle,
                    authRepository = app.container.authRepository,
                    profileReviewRepository = app.container.profileReviewRepository
                )
            }
        }
    }
}
