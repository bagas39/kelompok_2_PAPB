package com.example.cloble.ui.screens.matched

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.data.remote.SupabaseDataInvalidator
import com.example.cloble.domain.model.VerifiedMatchDetail
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MatchedUiState(
    val matchId: Long = -1L,
    val matchDetail: VerifiedMatchDetail? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class MatchedViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository,
    private val dataInvalidator: SupabaseDataInvalidator? = null
) : ViewModel() {

    private val matchId: Long = savedStateHandle.get<Long>("matchId") ?: -1L

    private val _uiState = MutableStateFlow(MatchedUiState(matchId = matchId))
    val uiState: StateFlow<MatchedUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null

    init {
        startObserving()
    }

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            try {
                val userId = authRepository.observeLoggedInUserId().firstOrNull()
                if (userId != null && matchId > 0L) {
                    discoveryMatchRepository.observeMatchDetail(matchId, userId)
                        .catch {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Gagal memuat data match. Periksa koneksi internet Anda lalu coba lagi."
                                )
                            }
                        }
                        .collect { detail ->
                            _uiState.update { it.copy(matchDetail = detail, isLoading = false, errorMessage = null) }
                        }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Gagal memuat data match. Periksa koneksi internet Anda lalu coba lagi."
                    )
                }
            }
        }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        dataInvalidator?.invalidateChat()
        startObserving()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication
                val handle = createSavedStateHandle()
                MatchedViewModel(
                    savedStateHandle = handle,
                    authRepository = app.container.authRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository,
                    dataInvalidator = app.container.dataInvalidator
                )
            }
        }
    }
}
