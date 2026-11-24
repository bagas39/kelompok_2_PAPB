package com.example.cloble.ui.screens.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.data.remote.SupabaseWardrobeRepository
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.WardrobeItemSummary
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.WardrobeMutationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WardrobeUiState(
    val items: List<WardrobeItemSummary> = emptyList(),
    val eligibleBarterCount: Int = 0,
    val unreadNotificationCount: Int = 0,
    val currentUserId: Long? = null,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val togglingItemIds: Set<Long> = emptySet(),
    val itemPendingDelete: ClothingItem? = null,
    val loadErrorMessage: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class WardrobeViewModel(
    private val authRepository: AuthRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WardrobeUiState())
    val uiState: StateFlow<WardrobeUiState> = _uiState.asStateFlow()
    private var wardrobeJob: Job? = null
    private var unreadJob: Job? = null
    private var hasSeenFirstResume: Boolean = false

    init {
        startObserving()
    }

    private fun startObserving() {
        wardrobeJob?.cancel()
        unreadJob?.cancel()

        wardrobeJob = viewModelScope.launch {
            authRepository.observeLoggedInUserId()
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    if (userId == null) {
                        flowOf(emptyList())
                    } else {
                        _uiState.update { it.copy(currentUserId = userId) }
                        wardrobeRepository.observeMyWardrobe(userId)
                    }
                }
                .collect { wardrobeItems ->
                    val eligible = wardrobeItems.count {
                        it.item.isBarterAllowed && !it.isInActiveMatch && it.item.status == ItemStatus.AVAILABLE
                    }
                    val fetchFailed = wardrobeItems.isEmpty() &&
                        (wardrobeRepository as? SupabaseWardrobeRepository)?.lastMyWardrobeFetchFailed == true
                    _uiState.update {
                        it.copy(
                            items = wardrobeItems,
                            eligibleBarterCount = eligible,
                            isLoading = false,
                            loadErrorMessage = if (fetchFailed) {
                                "Gagal memuat koleksi lemari. Periksa koneksi internet Anda lalu coba lagi."
                            } else {
                                null
                            }
                        )
                    }
                }
        }

        unreadJob = viewModelScope.launch {
            authRepository.observeLoggedInUserId()
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    if (userId == null) flowOf(0)
                    else discoveryMatchRepository.observeUnreadNotificationCount(userId)
                }
                .collect { count ->
                    _uiState.update { it.copy(unreadNotificationCount = count) }
                }
        }
    }

    fun retryLoadWardrobe() {
        _uiState.update { it.copy(isLoading = true, loadErrorMessage = null, errorMessage = null) }
        (wardrobeRepository as? SupabaseWardrobeRepository)?.triggerWardrobeRefresh()
        startObserving()
    }

    fun toggleBarterAllowed(itemId: Long, currentAllowed: Boolean) {
        if (itemId in _uiState.value.togglingItemIds) return
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(togglingItemIds = it.togglingItemIds + itemId, errorMessage = null) }
            try {
                when (val result = wardrobeRepository.setBarterAllowed(userId, itemId, !currentAllowed)) {
                    is WardrobeMutationResult.Success -> {
                        _uiState.update { it.copy(errorMessage = null) }
                    }
                    is WardrobeMutationResult.LockedInActiveMatch -> {
                        _uiState.update {
                            it.copy(errorMessage = "Pakaian sedang terikat Match aktif dan tidak dapat diubah izin barternya.")
                        }
                    }
                    is WardrobeMutationResult.ValidationFailed -> {
                        _uiState.update { it.copy(errorMessage = result.message) }
                    }
                    is WardrobeMutationResult.DatabaseError -> {
                        _uiState.update {
                            it.copy(
                                errorMessage = result.failure.message
                                    ?: "Gagal mengubah izin barter pakaian. Periksa koneksi internet Anda."
                            )
                        }
                    }
                    else -> {
                        _uiState.update {
                            it.copy(errorMessage = "Gagal mengubah izin barter pakaian. Periksa koneksi internet Anda.")
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(togglingItemIds = it.togglingItemIds - itemId) }
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
        val state = _uiState.value
        if (state.isDeleting) return
        val item = state.itemPendingDelete ?: return
        val userId = state.currentUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            try {
                when (val result = wardrobeRepository.softDeleteClothingItem(userId, item.id)) {
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
                    is WardrobeMutationResult.DatabaseError -> {
                        _uiState.update {
                            it.copy(
                                itemPendingDelete = null,
                                errorMessage = result.failure.message ?: "Gagal menghapus pakaian."
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
            } finally {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }

    fun onScreenResumed() {
        clearErrorMessage()
        if (!hasSeenFirstResume) {
            hasSeenFirstResume = true
            return
        }
        (wardrobeRepository as? SupabaseWardrobeRepository)?.triggerWardrobeRefresh()
        startObserving()
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
                    wardrobeRepository = app.container.wardrobeRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository
                )
            }
        }
    }
}
