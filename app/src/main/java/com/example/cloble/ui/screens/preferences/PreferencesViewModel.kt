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

data class PreferencesUiState(
    val isOnboarding: Boolean = false,
    val selectedCity: String = CityPreset.BANDUNG.displayName,
    val selectedLatitude: Double = CityPreset.BANDUNG.latitude,
    val selectedLongitude: Double = CityPreset.BANDUNG.longitude,
    val selectedRadiusKm: Int = 10,
    val selectedSize: String = "M",
    val selectedGender: String = "Uniseks",
    val isSaving: Boolean = false,
    val isSavedSuccess: Boolean = false,
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
                selectedLongitude = longitude
            )
        }
    }

    fun onRadiusSelected(radiusKm: Int) {
        _uiState.update { it.copy(selectedRadiusKm = radiusKm) }
    }

    fun onSizeSelected(size: String) {
        _uiState.update { it.copy(selectedSize = size) }
    }

    fun onGenderSelected(gender: String) {
        _uiState.update { it.copy(selectedGender = gender) }
    }

    fun savePreferences(onSuccess: () -> Unit) {
        val s = _uiState.value
        val userId = s.currentUserId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val prefs = UserPreferences(
                preferredSize = s.selectedSize,
                preferredGender = s.selectedGender,
                city = s.selectedCity.trim(),
                latitude = s.selectedLatitude,
                longitude = s.selectedLongitude,
                preferredRadiusKm = s.selectedRadiusKm
            )
            profileReviewRepository.updatePreferences(userId, prefs)
            _uiState.update { it.copy(isSaving = false, isSavedSuccess = true) }
            onSuccess()
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
