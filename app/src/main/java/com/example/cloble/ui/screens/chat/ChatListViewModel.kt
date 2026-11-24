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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListUiState(
    val threads: List<ChatThreadSummary> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
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
    private var threadsJob: Job? = null

    init {
        startObservingThreads()

        viewModelScope.launch {
            authRepository.observeLoggedInUserId()
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    if (userId == null) {
                        flowOf(0)
                    } else {
                        discoveryMatchRepository.observeUnreadNotificationCount(userId)
                    }
                }
                .catch { }
                .collect { count ->
                    _uiState.update { it.copy(unreadNotificationCount = count) }
                }
        }
    }

    private fun startObservingThreads() {
        threadsJob?.cancel()
        threadsJob = viewModelScope.launch {
            authRepository.observeLoggedInUserId()
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    if (userId == null) {
                        _uiState.update {
                            it.copy(
                                currentUserId = null,
                                threads = emptyList(),
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                        flowOf(emptyList())
                    } else {
                        _uiState.update { it.copy(currentUserId = userId) }
                        chatRepository.observeChatThreads(userId)
                    }
                }
                .catch {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Gagal memuat daftar percakapan. Periksa koneksi internet Anda lalu coba lagi."
                        )
                    }
                }
                .collect { list ->
                    _uiState.update { it.copy(threads = list, isLoading = false, errorMessage = null) }
                }
        }
    }

    fun refresh() {
        dataInvalidator?.invalidateChat()
        dataInvalidator?.invalidateNotifications()
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        refresh()
        startObservingThreads()
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
