package com.example.cloble.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.ActivityHistoryTab
import com.example.cloble.domain.model.OutgoingInterestSummary
import com.example.cloble.domain.model.PassedClothingSummary
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.result.CancelInterestResult
import com.example.cloble.domain.result.SendInterestResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ActivityHistoryEvent {
    data class MutualMatchCreated(val matchId: Long) : ActivityHistoryEvent
}

data class ActivityHistoryUiState(
    val selectedTab: ActivityHistoryTab = ActivityHistoryTab.TERTARIK,
    val outgoingInterests: List<OutgoingInterestSummary> = emptyList(),
    val passedItems: List<PassedClothingSummary> = emptyList(),
    val isLoading: Boolean = true,
    val cancellingInterestId: Long? = null,
    val processingPassItemIds: Set<Long> = emptySet(),
    val actionMessage: String? = null,
    val errorMessage: String? = null,
    val currentUserId: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityHistoryViewModel(
    private val authRepository: AuthRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityHistoryUiState())
    val uiState: StateFlow<ActivityHistoryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ActivityHistoryEvent>()
    val events: SharedFlow<ActivityHistoryEvent> = _events.asSharedFlow()

    private var observationJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.observeLoggedInUserId()
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId == null) {
                        observationJob?.cancel()
                        _uiState.update {
                            it.copy(
                                currentUserId = null,
                                outgoingInterests = emptyList(),
                                passedItems = emptyList(),
                                isLoading = false
                            )
                        }
                    } else {
                        _uiState.update { it.copy(currentUserId = userId, isLoading = true) }
                        startObservingHistory(userId)
                    }
                }
        }
    }

    private fun startObservingHistory(userId: Long) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            combine(
                discoveryMatchRepository.observeOutgoingInterests(userId),
                discoveryMatchRepository.observePassedClothing(userId)
            ) { interests, passed ->
                interests to passed
            }.collect { (interests, passed) ->
                _uiState.update {
                    it.copy(
                        outgoingInterests = interests,
                        passedItems = passed,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectTab(tab: ActivityHistoryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun cancelOutgoingInterest(interestId: Long) {
        if (_uiState.value.cancellingInterestId != null) return
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cancellingInterestId = interestId,
                    actionMessage = null,
                    errorMessage = null
                )
            }
            when (discoveryMatchRepository.cancelOutgoingInterest(userId, interestId)) {
                is CancelInterestResult.Success -> {
                    _uiState.update {
                        it.copy(
                            cancellingInterestId = null,
                            actionMessage = "Minat berhasil dibatalkan. Pakaian kembali tersedia di Jelajah.",
                            errorMessage = null
                        )
                    }
                }
                is CancelInterestResult.InterestAlreadyProcessed -> {
                    _uiState.update {
                        it.copy(
                            cancellingInterestId = null,
                            errorMessage = "Minat ini sudah diproses oleh pemilik sehingga tidak dapat dibatalkan.",
                            actionMessage = null
                        )
                    }
                }
                is CancelInterestResult.InterestNotFound -> {
                    _uiState.update {
                        it.copy(
                            cancellingInterestId = null,
                            errorMessage = "Data minat tidak ditemukan atau sudah dibatalkan.",
                            actionMessage = null
                        )
                    }
                }
                is CancelInterestResult.NotInterestOwner -> {
                    _uiState.update {
                        it.copy(
                            cancellingInterestId = null,
                            errorMessage = "Anda tidak memiliki izin membatalkan minat ini.",
                            actionMessage = null
                        )
                    }
                }
                is CancelInterestResult.DatabaseError -> {
                    _uiState.update {
                        it.copy(
                            cancellingInterestId = null,
                            errorMessage = "Gagal membatalkan minat. Periksa koneksi internet Anda dan coba lagi.",
                            actionMessage = null
                        )
                    }
                }
            }
        }
    }

    fun convertPassToInterest(targetItemId: Long) {
        val current = _uiState.value
        val userId = current.currentUserId ?: return
        if (targetItemId in current.processingPassItemIds) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    processingPassItemIds = it.processingPassItemIds + targetItemId,
                    actionMessage = null,
                    errorMessage = null
                )
            }
            try {
                when (val result = discoveryMatchRepository.convertPassToInterest(userId, targetItemId)) {
                    is SendInterestResult.Success -> {
                        val createdMatchId = result.matchId
                        if (createdMatchId != null && createdMatchId > 0L) {
                            _uiState.update {
                                it.copy(
                                    actionMessage = "Match terbentuk! Membuka detail pertukaran...",
                                    errorMessage = null
                                )
                            }
                            _events.emit(ActivityHistoryEvent.MutualMatchCreated(createdMatchId))
                        } else {
                            _uiState.update {
                                it.copy(
                                    actionMessage = "Berhasil mengirim Tertarik!",
                                    errorMessage = null
                                )
                            }
                        }
                    }
                    is SendInterestResult.AlreadyInterested -> {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Pakaian ini sudah pernah ditandai Tertarik sebelumnya.",
                                actionMessage = null
                            )
                        }
                    }
                    is SendInterestResult.RequiresBarterableItemInWardrobe -> {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Tambahkan pakaian berstatus barter di lemari terlebih dahulu.",
                                actionMessage = null
                            )
                        }
                    }
                    is SendInterestResult.TargetItemNotFound -> {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Pakaian tidak ditemukan.",
                                actionMessage = null
                            )
                        }
                    }
                    is SendInterestResult.TargetItemNotEligible -> {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Pakaian ini sudah tidak tersedia untuk barter.",
                                actionMessage = null
                            )
                        }
                    }
                    is SendInterestResult.PairAlreadyMatched -> {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Pasangan pakaian ini sudah memiliki pertukaran aktif atau sudah selesai ditukar.",
                                actionMessage = null
                            )
                        }
                    }
                    is SendInterestResult.CannotInterestOwnItem -> {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Tidak dapat mengirim Tertarik pada pakaian sendiri.",
                                actionMessage = null
                            )
                        }
                    }
                    is SendInterestResult.InvalidOrMissingSenderSession -> {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Sesi pengguna tidak valid.",
                                actionMessage = null
                            )
                        }
                    }
                    is SendInterestResult.DatabaseError -> {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Gagal mengirim tawaran. Periksa koneksi internet Anda dan coba lagi.",
                                actionMessage = null
                            )
                        }
                    }
                }
            } finally {
                _uiState.update {
                    it.copy(processingPassItemIds = it.processingPassItemIds - targetItemId)
                }
            }
        }
    }

    fun restorePassToDiscover(targetItemId: Long) {
        val current = _uiState.value
        val userId = current.currentUserId ?: return
        if (targetItemId in current.processingPassItemIds) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    processingPassItemIds = it.processingPassItemIds + targetItemId,
                    actionMessage = null,
                    errorMessage = null
                )
            }
            try {
                val success = discoveryMatchRepository.restorePassToDiscover(userId, targetItemId)
                if (success) {
                    _uiState.update {
                        it.copy(
                            actionMessage = "Pakaian dikembalikan ke feed Discover.",
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            errorMessage = "Gagal mengembalikan pakaian ke feed.",
                            actionMessage = null
                        )
                    }
                }
            } finally {
                _uiState.update {
                    it.copy(processingPassItemIds = it.processingPassItemIds - targetItemId)
                }
            }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication
                ActivityHistoryViewModel(
                    authRepository = app.container.authRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository
                )
            }
        }
    }
}
