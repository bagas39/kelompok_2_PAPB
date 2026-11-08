package com.example.cloble.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.domain.model.DiscoverFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FilterUiState(
    val selectedSizes: Set<String> = emptySet(),
    val selectedGenders: Set<String> = emptySet(),
    val selectedCategories: Set<String> = emptySet(),
    val selectedRadiusKm: Int? = null
)

class FilterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FilterUiState())
    val uiState: StateFlow<FilterUiState> = _uiState.asStateFlow()

    fun toggleCategory(category: String) {
        _uiState.update { current ->
            val set = current.selectedCategories.toMutableSet()
            if (set.contains(category)) set.remove(category) else set.add(category)
            current.copy(selectedCategories = set)
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

    fun setRadius(radius: Int?) {
        _uiState.update { it.copy(selectedRadiusKm = radius) }
    }

    fun reset() {
        _uiState.value = FilterUiState()
    }

    fun buildFilter(): DiscoverFilter {
        val state = _uiState.value
        return DiscoverFilter(
            selectedSizes = state.selectedSizes,
            selectedGenders = state.selectedGenders,
            selectedCategories = state.selectedCategories,
            maxRadiusKm = state.selectedRadiusKm
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FilterViewModel()
            }
        }
    }
}
