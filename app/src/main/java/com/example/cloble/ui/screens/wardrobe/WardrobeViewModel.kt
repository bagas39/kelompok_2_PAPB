package com.example.cloble.ui.screens.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.WardrobeItemSummary
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.WardrobeMutationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WardrobeUiState(
    val items: List<WardrobeItemSummary> = emptyList(),
    val eligibleBarterCount: Int = 0,
    val currentUserId: Long? = null,
    val isLoading: Boolean = true,
    val itemPendingDelete: ClothingItem? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class WardrobeViewModel(
    private val authRepository: AuthRepository,
    private val wardrobeRepository: WardrobeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WardrobeUiState())
    val uiState: StateFlow<WardrobeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeLoggedInUserId().flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    _uiState.update { it.copy(currentUserId = userId) }
                    wardrobeRepository.observeMyWardrobe(userId)
                }
            }.collect { wardrobeItems ->
                val eligible = wardrobeItems.count { it.item.isBarterAllowed && !it.isInActiveMatch && it.item.status == com.example.cloble.domain.model.ItemStatus.AVAILABLE }
                _uiState.update {
                    it.copy(
                        items = wardrobeItems,
                        eligibleBarterCount = eligible,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun toggleBarterAllowed(itemId: Long, currentAllowed: Boolean) {
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            val result = wardrobeRepository.setBarterAllowed(userId, itemId, !currentAllowed)
            if (result is WardrobeMutationResult.LockedInActiveMatch) {
                _uiState.update { it.copy(errorMessage = "Pakaian sedang terikat Match aktif dan tidak dapat diubah izin barternya.") }
            }
        }
    }

    fun requestDelete(item: ClothingItem) {
        _uiState.update { it.copy(itemPendingDelete = item, errorMessage = null) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(itemPendingDelete = null) }
    }

    fun confirmDelete() {
        val item = _uiState.value.itemPendingDelete ?: return
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            when (val res = wardrobeRepository.softDeleteClothingItem(userId, item.id)) {
                is WardrobeMutationResult.Success -> {
                    _uiState.update { it.copy(itemPendingDelete = null) }
                }
                is WardrobeMutationResult.LockedInActiveMatch -> {
                    _uiState.update {
                        it.copy(
                            itemPendingDelete = null,
                            errorMessage = "Tidak dapat menghapus pakaian yang sedang terikat Match aktif."
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            itemPendingDelete = null,
                            errorMessage = "Gagal menghapus pakaian."
                        )
                    }
                }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun showLockedItemWarning(itemName: String, isInActiveMatch: Boolean) {
        val reason = if (isInActiveMatch) {
            "Pakaian \"$itemName\" sedang terikat Match aktif sehingga tidak dapat diubah atau dihapus sebelum Match selesai/dibatalkan."
        } else {
            "Pakaian \"$itemName\" sudah selesai ditukar sehingga tidak dapat diubah lagi."
        }
        _uiState.update { it.copy(errorMessage = reason) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication)
                WardrobeViewModel(
                    authRepository = app.container.authRepository,
                    wardrobeRepository = app.container.wardrobeRepository
                )
            }
        }
    }
}
