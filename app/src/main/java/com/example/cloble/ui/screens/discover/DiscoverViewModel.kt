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
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.SendInterestResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
    val isActionInProgress: Boolean = false,
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
    data class OpenMatch(val matchId: Long) : DiscoverNavigationEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModel(
    private val authRepository: AuthRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository,
    private val discoverFilterFlow: MutableStateFlow<DiscoverFilter> = MutableStateFlow(DiscoverFilter()),
    private val dataInvalidator: SupabaseDataInvalidator? = null,
    private val wardrobeRepository: WardrobeRepository? = null
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
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    _uiState.update { current ->
                        if (current.currentUserId != userId) {
                            current.copy(
                                currentUserId = userId, candidates = emptyList(), currentIndex = 0,
                                unreadNotificationCount = 0, isLoading = userId != null,
                                bannerMessage = null, requiresBarterItemCta = false
                            )
                        } else current
                    }
                    if (userId == null) flowOf(0) else discoveryMatchRepository.observeUnreadNotificationCount(userId)
                }
                .collect { count ->
                    _uiState.update { it.copy(unreadNotificationCount = count) }
                }
        }

        viewModelScope.launch {
            discoverFilterFlow.collect { latestFilter ->
                _uiState.update { current ->
                    if (current.filter == latestFilter) {
                        current
                    } else {
                        current.copy(
                            filter = latestFilter,
                            candidates = emptyList(),
                            currentIndex = 0,
                            isLoading = true
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            var lastCandidatesUserId: Long? = null
            var lastCandidatesFilter: DiscoverFilter? = null
            authRepository.observeLoggedInUserId()
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    if (userId == null) {
                        flowOf(
                            Triple<Long?, DiscoverFilter, List<DiscoverCandidate>>(
                                null,
                                discoverFilterFlow.value,
                                emptyList()
                            )
                        )
                    } else {
                        discoverFilterFlow.flatMapLatest { filter ->
                            discoveryMatchRepository.observeDiscoverCandidates(userId, filter)
                                .map { candidates -> Triple(userId, filter, candidates) }
                        }
                    }
                }
                .collect { (emittedUserId, emittedFilter, candidates) ->
                    val resetToHead = lastCandidatesUserId != emittedUserId ||
                        (lastCandidatesFilter != null && lastCandidatesFilter != emittedFilter)
                    lastCandidatesUserId = emittedUserId
                    lastCandidatesFilter = emittedFilter
                    _uiState.update { current ->
                        val currentItem = if (resetToHead) null else current.currentCandidate
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
        val current = _uiState.value
        val userId = current.currentUserId
        if (current.requiresBarterItemCta && userId != null && wardrobeRepository != null) {
            viewModelScope.launch {
                if (wardrobeRepository.countEligibleBarterItems(userId) > 0) {
                    _uiState.update {
                        it.copy(
                            bannerMessage = null,
                            requiresBarterItemCta = false
                        )
                    }
                }
            }
        }
    }

    fun passCurrentCandidate() {
        val state = _uiState.value
        if (state.isActionInProgress) return
        val candidate = state.currentCandidate ?: return
        val userId = state.currentUserId ?: return

        _uiState.update { it.copy(isActionInProgress = true) }
        viewModelScope.launch {
            try {
                val saved = discoveryMatchRepository.passCandidate(userId, candidate.item.id)
                _uiState.update { current ->
                    if (saved) {
                        val remaining = current.candidates.filterNot { it.item.id == candidate.item.id }
                        val nextIdx = if (remaining.isEmpty()) 0 else current.currentIndex.coerceIn(0, remaining.lastIndex)
                        current.copy(
                            candidates = remaining,
                            currentIndex = nextIdx,
                            bannerMessage = null,
                            requiresBarterItemCta = false
                        )
                    } else {
                        current.copy(
                            bannerMessage = "Gagal menyimpan aksi lewati. Periksa koneksi internet Anda.",
                            requiresBarterItemCta = false
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(isActionInProgress = false) }
            }
        }
    }

    fun likeCurrentCandidate() {
        val state = _uiState.value
        if (state.isActionInProgress) return
        val candidate = state.currentCandidate ?: return
        val userId = state.currentUserId ?: return

        _uiState.update { it.copy(isActionInProgress = true) }
        viewModelScope.launch {
            try {
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
                        if (result.matchId != null) {
                            _events.emit(DiscoverNavigationEvent.OpenMatch(result.matchId))
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
                    is SendInterestResult.PairAlreadyMatched -> {
                        _uiState.update {
                            it.copy(
                                bannerMessage = "Pasangan pakaian ini sudah memiliki pertukaran aktif atau sudah selesai ditukar.",
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
            } finally {
                _uiState.update { it.copy(isActionInProgress = false) }
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
                    dataInvalidator = app.container.dataInvalidator,
                    wardrobeRepository = app.container.wardrobeRepository
                )
            }
        }
    }
}
