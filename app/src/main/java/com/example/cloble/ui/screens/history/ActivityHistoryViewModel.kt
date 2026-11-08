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
import com.example.cloble.domain.result.SendInterestResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityHistoryUiState(
    val selectedTab: ActivityHistoryTab = ActivityHistoryTab.TERTARIK,
    val outgoingInterests: List<OutgoingInterestSummary> = emptyList(),
    val passedItems: List<PassedClothingSummary> = emptyList(),
    val isLoading: Boolean = true,
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

    private var observationJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.observeLoggedInUserId()
                .filterNotNull()
                .distinctUntilChanged()
                .collect { userId ->
                    _uiState.update { it.copy(currentUserId = userId, isLoading = true) }
                    startObservingHistory(userId)
                }
        }
    }

    private fun startObservingHistory(userId: Long) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            launch {
                discoveryMatchRepository.observeOutgoingInterests(userId).collect { interests ->
                    _uiState.update {
                        it.copy(
                            outgoingInterests = interests,
                            isLoading = false
                        )
                    }
                }
            }
            launch {
                discoveryMatchRepository.observePassedClothing(userId).collect { passed ->
                    _uiState.update {
                        it.copy(
                            passedItems = passed,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun selectTab(tab: ActivityHistoryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun convertPassToInterest(targetItemId: Long) {
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            when (val result = discoveryMatchRepository.convertPassToInterest(userId, targetItemId)) {
                is SendInterestResult.Success -> {
                    _uiState.update {
                        it.copy(
                            actionMessage = "Berhasil mengirim Tertarik!",
                            errorMessage = null
                        )
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
                            errorMessage = "Terjadi kendala penyimpanan: ${result.failure.message ?: "Kesalahan database"}",
                            actionMessage = null
                        )
                    }
                }
            }
        }
    }

    fun restorePassToDiscover(targetItemId: Long) {
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
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
