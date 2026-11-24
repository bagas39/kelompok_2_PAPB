package com.example.cloble.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.data.remote.SupabaseDataInvalidator
import com.example.cloble.domain.model.NotificationFeedItem
import com.example.cloble.domain.model.NotificationType
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
import java.util.concurrent.atomic.AtomicBoolean

data class NotificationsUiState(
    val notifications: List<NotificationFeedItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val currentUserId: Long? = null
)

class NotificationsViewModel(
    private val authRepository: AuthRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository,
    private val dataInvalidator: SupabaseDataInvalidator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val isMarkingRead = AtomicBoolean(false)
    private var lastAutoMarkedIds: Set<Long> = emptySet()
    private var observeJob: Job? = null

    init {
        startObserving()
    }

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            try {
                val userId = authRepository.observeLoggedInUserId().firstOrNull()
                if (userId != null) {
                    _uiState.update { it.copy(currentUserId = userId) }

                    discoveryMatchRepository.observeNotifications(userId)
                        .catch {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Gagal memuat notifikasi. Periksa koneksi internet Anda lalu coba lagi."
                                )
                            }
                        }
                        .collect { list ->
                            _uiState.update { it.copy(notifications = list, isLoading = false, errorMessage = null) }
                            val autoMarkIds = list.filter { notification ->
                                !notification.isRead &&
                                    !(notification.type == NotificationType.INCOMING_INTEREST && notification.canReplyToInterest)
                            }.map { it.id }.toSet()
                            if (autoMarkIds.isNotEmpty() && autoMarkIds != lastAutoMarkedIds && isMarkingRead.compareAndSet(false, true)) {
                                lastAutoMarkedIds = autoMarkIds
                                launch {
                                    try {
                                        discoveryMatchRepository.markNotificationsRead(userId)
                                    } finally {
                                        isMarkingRead.set(false)
                                    }
                                }
                            }
                        }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Gagal memuat notifikasi. Periksa koneksi internet Anda lalu coba lagi."
                    )
                }
            }
        }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        dataInvalidator?.invalidateNotifications()
        startObserving()
    }

    fun markNotificationRead(notificationId: Long) {
        viewModelScope.launch {
            discoveryMatchRepository.markNotificationReadById(notificationId)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication
                NotificationsViewModel(
                    authRepository = app.container.authRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository,
                    dataInvalidator = app.container.dataInvalidator
                )
            }
        }
    }
}
