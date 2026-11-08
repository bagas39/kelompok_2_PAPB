package com.example.cloble.ui.screens.discover

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.SendInterestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemDetailUiState(
    val itemId: Long = -1L,
    val item: ClothingItem? = null,
    val owner: UserProfile? = null,
    val isOwnItem: Boolean = false,
    val isLoading: Boolean = true,
    val isSendingInterest: Boolean = false,
    val interestSentSuccess: Boolean = false,
    val errorMessage: String? = null,
    val requiresBarterItemCta: Boolean = false
)

class ItemDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val profileReviewRepository: ProfileReviewRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository
) : ViewModel() {

    private val rawItemId: Long = savedStateHandle.get<Long>("itemId") ?: -1L

    private val _uiState = MutableStateFlow(ItemDetailUiState(itemId = rawItemId))
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            val currentUserId = authRepository.observeLoggedInUserId().firstOrNull()
            val item = wardrobeRepository.getClothingItem(rawItemId)
            if (item != null) {
                val owner = profileReviewRepository.observeUserProfile(item.ownerId).firstOrNull()
                _uiState.update {
                    it.copy(
                        item = item,
                        owner = owner,
                        isOwnItem = (currentUserId != null && currentUserId == item.ownerId),
                        isLoading = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Pakaian tidak ditemukan."
                    )
                }
            }
        }
    }

    fun sendInterest() {
        val state = _uiState.value
        val item = state.item ?: return

        viewModelScope.launch {
            val currentUserId = authRepository.observeLoggedInUserId().firstOrNull() ?: return@launch
            _uiState.update { it.copy(isSendingInterest = true, errorMessage = null) }

            val result = discoveryMatchRepository.sendInterest(currentUserId, item.id)
            when (result) {
                is SendInterestResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            interestSentSuccess = true,
                            requiresBarterItemCta = false
                        )
                    }
                }
                is SendInterestResult.RequiresBarterableItemInWardrobe -> {
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            errorMessage = "Anda belum memiliki pakaian yang diizinkan untuk barter di Lemari. Tambahkan pakaian barter terlebih dahulu.",
                            requiresBarterItemCta = true
                        )
                    }
                }
                is SendInterestResult.AlreadyInterested -> {
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            errorMessage = "Anda sudah pernah mengirim tawaran interest untuk pakaian ini.",
                            requiresBarterItemCta = false
                        )
                    }
                }
                is SendInterestResult.TargetItemNotEligible -> {
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            errorMessage = "Pakaian ini sudah tidak tersedia untuk dibarter.",
                            requiresBarterItemCta = false
                        )
                    }
                }
                is SendInterestResult.CannotInterestOwnItem -> {
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            errorMessage = "Ini adalah pakaian milik Anda sendiri.",
                            requiresBarterItemCta = false
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            errorMessage = "Gagal mengirim interest. Silakan coba lagi.",
                            requiresBarterItemCta = false
                        )
                    }
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication
                val handle = createSavedStateHandle()
                ItemDetailViewModel(
                    savedStateHandle = handle,
                    authRepository = app.container.authRepository,
                    wardrobeRepository = app.container.wardrobeRepository,
                    profileReviewRepository = app.container.profileReviewRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository
                )
            }
        }
    }
}
