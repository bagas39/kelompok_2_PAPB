package com.example.cloble.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.NotificationFeedItem
import com.example.cloble.domain.model.NotificationType
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class NotificationsUiState(
    val notifications: List<NotificationFeedItem> = emptyList(),
    val isLoading: Boolean = true,
    val currentUserId: Long? = null
)

class NotificationsViewModel(
    private val authRepository: AuthRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val isMarkingRead = AtomicBoolean(false)
    private var lastAutoMarkedIds: Set<Long> = emptySet()

    init {
        viewModelScope.launch {
            val userId = authRepository.observeLoggedInUserId().firstOrNull()
            if (userId != null) {
                _uiState.update { it.copy(currentUserId = userId) }

                discoveryMatchRepository.observeNotifications(userId).collect { list ->
                    _uiState.update { it.copy(notifications = list, isLoading = false) }
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
                _uiState.update { it.copy(isLoading = false) }
            }
        }
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
                    discoveryMatchRepository = app.container.discoveryMatchRepository
                )
            }
        }
    }
}
