package com.example.cloble.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.DiscoverFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private val PRESET_RADII_KM = setOf(5, 10, 25, 50, 100)

data class FilterUiState(
    val selectedColors: Set<String> = emptySet(),
    val selectedSizes: Set<String> = emptySet(),
    val selectedGenders: Set<String> = emptySet(),
    val selectedCategories: Set<String> = emptySet(),
    val selectedRadiusKm: Int? = null,
    val isCustomRadius: Boolean = false,
    val customRadiusText: String = "",
    val errorMessage: String? = null
)

class FilterViewModel(
    private val discoverFilterFlow: MutableStateFlow<DiscoverFilter> = MutableStateFlow(DiscoverFilter())
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        discoverFilterFlow.value.let { current ->
            val radius = current.maxRadiusKm
            val isCustom = radius != null && !PRESET_RADII_KM.contains(radius)
            FilterUiState(
                selectedColors = current.selectedColors,
                selectedSizes = current.selectedSizes,
                selectedGenders = current.selectedGenders,
                selectedCategories = current.selectedCategories,
                selectedRadiusKm = radius,
                isCustomRadius = isCustom,
                customRadiusText = if (isCustom) radius.toString() else ""
            )
        }
    )
    val uiState: StateFlow<FilterUiState> = _uiState.asStateFlow()

    fun toggleCategory(category: String) {
        _uiState.update { current ->
            val set = current.selectedCategories.toMutableSet()
            if (set.contains(category)) set.remove(category) else set.add(category)
            current.copy(selectedCategories = set)
        }
    }

    fun toggleColor(color: String) {
        _uiState.update { current ->
            val colors = current.selectedColors.toMutableSet()
            if (!colors.add(color)) colors.remove(color)
            current.copy(selectedColors = colors)
        }
    }

    fun toggleSize(size: String) {
        _uiState.update { current ->
            val set = current.selectedSizes.toMutableSet()
            if (set.contains(size)) set.remove(size) else set.add(size)
            current.copy(selectedSizes = set)
        }
    }

    fun toggleGender(gender: String) {
        _uiState.update { current ->
            val set = current.selectedGenders.toMutableSet()
            if (set.contains(gender)) set.remove(gender) else set.add(gender)
            current.copy(selectedGenders = set)
        }
    }

    fun selectPresetRadius(km: Int) {
        _uiState.update { current ->
            if (!current.isCustomRadius && current.selectedRadiusKm == km) {
                current.copy(selectedRadiusKm = null, isCustomRadius = false, errorMessage = null)
            } else {
                current.copy(selectedRadiusKm = km, isCustomRadius = false, errorMessage = null)
            }
        }
    }

    fun toggleCustomRadiusMode() {
        _uiState.update { current ->
            if (current.isCustomRadius) {
                current.copy(isCustomRadius = false, selectedRadiusKm = null, errorMessage = null)
            } else {
                val prefillText = current.customRadiusText.ifBlank {
                    current.selectedRadiusKm?.takeIf { it > 0 }?.toString().orEmpty()
                }
                val parsed = prefillText.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(20000)
                current.copy(
                    isCustomRadius = true,
                    customRadiusText = prefillText,
                    selectedRadiusKm = parsed,
                    errorMessage = null
                )
            }
        }
    }

    fun onCustomRadiusTextChanged(raw: String) {
        val digitsOnly = raw.filter { it.isDigit() }.take(5)
        val parsed = digitsOnly.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(20000)
        _uiState.update {
            it.copy(
                isCustomRadius = true,
                customRadiusText = digitsOnly,
                selectedRadiusKm = parsed,
                errorMessage = null
            )
        }
    }

    fun setRadius(radius: Int?) {
        val sanitized = radius?.takeIf { it > 0 }?.coerceAtMost(20000)
        val isCustom = sanitized != null && !PRESET_RADII_KM.contains(sanitized)
        _uiState.update {
            it.copy(
                selectedRadiusKm = sanitized,
                isCustomRadius = isCustom,
                customRadiusText = if (isCustom) sanitized.toString() else it.customRadiusText,
                errorMessage = null
            )
        }
    }

    fun reset() {
        _uiState.value = FilterUiState()
        discoverFilterFlow.value = DiscoverFilter()
    }

    fun applyFilter(): DiscoverFilter? {
        val state = _uiState.value
        if (state.isCustomRadius) {
            val parsedRadius = state.customRadiusText.toIntOrNull() ?: 0
            if (parsedRadius <= 0) {
                _uiState.update {
                    it.copy(errorMessage = "Radius pencarian harus diisi lebih dari 0 km.")
                }
                return null
            }
        }
        val built = buildFilter()
        _uiState.update { it.copy(errorMessage = null) }
        discoverFilterFlow.value = built
        return built
    }

    fun buildFilter(): DiscoverFilter {
        val state = _uiState.value
        val effectiveRadius = if (state.isCustomRadius) {
            state.customRadiusText.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(20000)
        } else {
            state.selectedRadiusKm?.takeIf { it > 0 }
        }
        return DiscoverFilter(
            selectedColors = state.selectedColors,
            selectedSizes = state.selectedSizes,
            selectedGenders = state.selectedGenders,
            selectedCategories = state.selectedCategories,
            maxRadiusKm = effectiveRadius
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication
                FilterViewModel(app.container.discoverFilterFlow)
            }
        }
    }
}
