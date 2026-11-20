package com.example.cloble.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.data.remote.SupabaseDataInvalidator
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListUiState(
    val threads: List<ChatThreadSummary> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val isLoading: Boolean = true,
    val currentUserId: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository,
    private val dataInvalidator: SupabaseDataInvalidator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeLoggedInUserId()
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    _uiState.update { it.copy(currentUserId = userId) }
                    chatRepository.observeChatThreads(userId)
                }
                .collect { list ->
                    _uiState.update { it.copy(threads = list, isLoading = false) }
                }
        }

        viewModelScope.launch {
            authRepository.observeLoggedInUserId()
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    discoveryMatchRepository.observeUnreadNotificationCount(userId)
                }
                .collect { count ->
                    _uiState.update { it.copy(unreadNotificationCount = count) }
                }
        }
    }

    fun refresh() {
        dataInvalidator?.invalidateChat()
        dataInvalidator?.invalidateNotifications()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication
                ChatListViewModel(
                    authRepository = app.container.authRepository,
                    chatRepository = app.container.chatRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository,
                    dataInvalidator = app.container.dataInvalidator
                )
            }
        }
    }
}
