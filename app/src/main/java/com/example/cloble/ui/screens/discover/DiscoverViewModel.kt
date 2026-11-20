package com.example.cloble.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.data.remote.SupabaseDataInvalidator
import com.example.cloble.domain.model.DiscoverCandidate
import com.example.cloble.domain.model.DiscoverFilter
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.result.SendInterestResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val candidates: List<DiscoverCandidate> = emptyList(),
    val currentIndex: Int = 0,
    val filter: DiscoverFilter = DiscoverFilter(),
    val viewerCity: String = "",
    val preferredRadiusKm: Int = 10,
    val unreadNotificationCount: Int = 0,
    val isLoading: Boolean = true,
    val bannerMessage: String? = null,
    val requiresBarterItemCta: Boolean = false,
    val currentUserId: Long? = null
) {
    val currentCandidate: DiscoverCandidate?
        get() = if (currentIndex in candidates.indices) candidates[currentIndex] else null

    val nextCandidate: DiscoverCandidate?
        get() = if (currentIndex + 1 in candidates.indices) candidates[currentIndex + 1] else null

    val hasMoreCards: Boolean
        get() = currentIndex < candidates.size

    val effectiveRadiusKm: Int
        get() = filter.maxRadiusKm ?: preferredRadiusKm

    val activeFilterCount: Int
        get() = (if (filter.maxRadiusKm != null) 1 else 0) +
            filter.selectedCategories.size +
            filter.selectedSizes.size +
            filter.selectedGenders.size +
            filter.selectedColors.size
}

sealed interface DiscoverNavigationEvent {
    data class OpenItemDetail(val itemId: Long) : DiscoverNavigationEvent
    data object OpenFilter : DiscoverNavigationEvent
    data object OpenNotifications : DiscoverNavigationEvent
    data object OpenWardrobeForAdd : DiscoverNavigationEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModel(
    private val authRepository: AuthRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository,
    private val discoverFilterFlow: MutableStateFlow<DiscoverFilter> = MutableStateFlow(DiscoverFilter()),
    private val dataInvalidator: SupabaseDataInvalidator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState(filter = discoverFilterFlow.value))
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DiscoverNavigationEvent>()
    val events: SharedFlow<DiscoverNavigationEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.observeCurrentUser().collect { user ->
                if (user != null) {
                    _uiState.update {
                        it.copy(
                            viewerCity = user.city,
                            preferredRadiusKm = user.preferredRadiusKm
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            authRepository.observeLoggedInUserId()
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    _uiState.update { it.copy(currentUserId = userId) }
                    discoveryMatchRepository.observeUnreadNotificationCount(userId)
                }
                .collect { count ->
                    _uiState.update { it.copy(unreadNotificationCount = count) }
                }
        }

        viewModelScope.launch {
            discoverFilterFlow.collect { latestFilter ->
                _uiState.update { it.copy(filter = latestFilter, currentIndex = 0, isLoading = true) }
            }
        }

        viewModelScope.launch {
            authRepository.observeLoggedInUserId()
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    discoverFilterFlow.flatMapLatest { filter ->
                        discoveryMatchRepository.observeDiscoverCandidates(userId, filter)
                    }
                }
                .collect { candidates ->
                    _uiState.update { current ->
                        val currentItem = current.currentCandidate
                        val newIndex = when {
                            candidates.isEmpty() -> 0
                            currentItem != null -> {
                                val found = candidates.indexOfFirst { it.item.id == currentItem.item.id }
                                if (found >= 0) found else current.currentIndex.coerceIn(0, candidates.lastIndex)
                            }
                            else -> 0
                        }
                        current.copy(
                            candidates = candidates,
                            currentIndex = newIndex,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun refreshDiscover() {
        dataInvalidator?.invalidateDiscovery()
    }

    fun passCurrentCandidate() {
        val state = _uiState.value
        val candidate = state.currentCandidate ?: return
        val userId = state.currentUserId ?: return

        viewModelScope.launch {
            discoveryMatchRepository.passCandidate(userId, candidate.item.id)
            _uiState.update { current ->
                val remaining = current.candidates.filterNot { it.item.id == candidate.item.id }
                val nextIdx = if (remaining.isEmpty()) 0 else current.currentIndex.coerceIn(0, remaining.lastIndex)
                current.copy(
                    candidates = remaining,
                    currentIndex = nextIdx,
                    bannerMessage = null,
                    requiresBarterItemCta = false
                )
            }
        }
    }

    fun likeCurrentCandidate() {
        val state = _uiState.value
        val candidate = state.currentCandidate ?: return
        val userId = state.currentUserId ?: return

        viewModelScope.launch {
            val result = discoveryMatchRepository.sendInterest(userId, candidate.item.id)
            when (result) {
                is SendInterestResult.Success -> {
                    val message = if (result.matchId != null) {
                        "Match terverifikasi! Anda dan ${candidate.ownerName} saling menyukai pakaian barter!"
                    } else {
                        "Tawaran interest untuk \"${candidate.item.name}\" berhasil dikirim!"
                    }
                    _uiState.update { current ->
                        val remaining = current.candidates.filterNot { it.item.id == candidate.item.id }
                        val nextIdx = if (remaining.isEmpty()) 0 else current.currentIndex.coerceIn(0, remaining.lastIndex)
                        current.copy(
                            candidates = remaining,
                            currentIndex = nextIdx,
                            bannerMessage = message,
                            requiresBarterItemCta = false
                        )
                    }
                }
                is SendInterestResult.RequiresBarterableItemInWardrobe -> {
                    _uiState.update {
                        it.copy(
                            bannerMessage = "Anda belum memiliki pakaian yang diizinkan untuk barter di Lemari. Tambahkan pakaian barter terlebih dahulu.",
                            requiresBarterItemCta = true
                        )
                    }
                }
                is SendInterestResult.AlreadyInterested -> {
                    _uiState.update { current ->
                        val remaining = current.candidates.filterNot { it.item.id == candidate.item.id }
                        val nextIdx = if (remaining.isEmpty()) 0 else current.currentIndex.coerceIn(0, remaining.lastIndex)
                        current.copy(
                            candidates = remaining,
                            currentIndex = nextIdx,
                            bannerMessage = "Anda sudah pernah mengirim ketertarikan untuk pakaian ini (${result.existingStatus.name}).",
                            requiresBarterItemCta = false
                        )
                    }
                }
                is SendInterestResult.TargetItemNotEligible -> {
                    _uiState.update { current ->
                        val remaining = current.candidates.filterNot { it.item.id == candidate.item.id }
                        val nextIdx = if (remaining.isEmpty()) 0 else current.currentIndex.coerceIn(0, remaining.lastIndex)
                        current.copy(
                            candidates = remaining,
                            currentIndex = nextIdx,
                            bannerMessage = "Pakaian ini sudah tidak tersedia untuk dibarter.",
                            requiresBarterItemCta = false
                        )
                    }
                }
                is SendInterestResult.CannotInterestOwnItem -> {
                    _uiState.update { current ->
                        val remaining = current.candidates.filterNot { it.item.id == candidate.item.id }
                        val nextIdx = if (remaining.isEmpty()) 0 else current.currentIndex.coerceIn(0, remaining.lastIndex)
                        current.copy(
                            candidates = remaining,
                            currentIndex = nextIdx,
                            bannerMessage = "Pakaian ini adalah milik Anda sendiri.",
                            requiresBarterItemCta = false
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            bannerMessage = "Gagal mengirim interest. Silakan coba lagi.",
                            requiresBarterItemCta = false
                        )
                    }
                }
            }
        }
    }

    fun updateFilter(newFilter: DiscoverFilter) {
        discoverFilterFlow.value = newFilter
    }

    fun resetFilter() {
        discoverFilterFlow.value = DiscoverFilter()
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null, requiresBarterItemCta = false) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication
                DiscoverViewModel(
                    authRepository = app.container.authRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository,
                    discoverFilterFlow = app.container.discoverFilterFlow,
                    dataInvalidator = app.container.dataInvalidator
                )
            }
        }
    }
}
