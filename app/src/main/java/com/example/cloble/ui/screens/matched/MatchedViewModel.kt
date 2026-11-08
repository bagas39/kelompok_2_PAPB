package com.example.cloble.ui.screens.matched

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.VerifiedMatchDetail
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MatchedUiState(
    val matchId: Long = -1L,
    val matchDetail: VerifiedMatchDetail? = null,
    val isLoading: Boolean = true
)

class MatchedViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository
) : ViewModel() {

    private val matchId: Long = savedStateHandle.get<Long>("matchId") ?: -1L

    private val _uiState = MutableStateFlow(MatchedUiState(matchId = matchId))
    val uiState: StateFlow<MatchedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = authRepository.observeLoggedInUserId().firstOrNull()
            if (userId != null && matchId > 0L) {
                discoveryMatchRepository.observeMatchDetail(matchId, userId).collect { detail ->
                    _uiState.update { it.copy(matchDetail = detail, isLoading = false) }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication
                val handle = createSavedStateHandle()
                MatchedViewModel(
                    savedStateHandle = handle,
                    authRepository = app.container.authRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository
                )
            }
        }
    }
}
