package com.example.cloble.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListUiState(
    val threads: List<ChatThreadSummary> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val isLoading: Boolean = true,
    val currentUserId: Long? = null
)

class ChatListViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = authRepository.observeLoggedInUserId().firstOrNull()
            if (userId != null) {
                _uiState.update { it.copy(currentUserId = userId) }

                launch {
                    discoveryMatchRepository.observeUnreadNotificationCount(userId).collect { count ->
                        _uiState.update { it.copy(unreadNotificationCount = count) }
                    }
                }

                launch {
                    chatRepository.observeChatThreads(userId).collect { list ->
                        _uiState.update { it.copy(threads = list, isLoading = false) }
                    }
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
                ChatListViewModel(
                    authRepository = app.container.authRepository,
                    chatRepository = app.container.chatRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository
                )
            }
        }
    }
}
