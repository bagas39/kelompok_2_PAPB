package com.example.cloble.ui.screens.preferences

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.CityPreset
import com.example.cloble.domain.model.UserPreferences
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
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

    init {
        viewModelScope.launch {
            val user = authRepository.observeCurrentUser().firstOrNull()
            if (user != null) {
                _uiState.update {
                    it.copy(
                        currentUserId = user.id,
                        selectedCity = user.city,
                        selectedLatitude = user.latitude,
                        selectedLongitude = user.longitude,
                        selectedRadiusKm = user.preferredRadiusKm,
                        isCustomRadius = user.preferredRadiusKm !in PRESET_RADII_KM,
                        customRadiusText = user.preferredRadiusKm.toString(),
                        selectedSize = user.preferredSize,
                        selectedGender = user.preferredGender
                    )
                }
            }
        }
    }

    fun onLocationSelected(city: String, latitude: Double, longitude: Double) {
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
        _uiState.update { it.copy(selectedSize = size, errorMessage = null) }
    }

    fun onGenderSelected(gender: String) {
        _uiState.update { it.copy(selectedGender = gender, errorMessage = null) }
    }

    fun savePreferences(onSuccess: () -> Unit) {
        val s = _uiState.value
        if (s.isSaving) return
        val userId = s.currentUserId ?: return
        val cleanCity = s.selectedCity.trim()

        if (cleanCity.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Lokasi tidak boleh kosong.") }
            return
        }

        val effectiveRadius = if (s.isCustomRadius) {
            s.customRadiusText.toIntOrNull() ?: 0
        } else {
            s.selectedRadiusKm
        }
        if (effectiveRadius <= 0) {
            _uiState.update {
                it.copy(errorMessage = "Radius pencarian harus diisi lebih dari 0 km.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
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
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSavedSuccess = false,
                        errorMessage = "Gagal menyimpan preferensi. Periksa koneksi internet Anda dan coba lagi."
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
