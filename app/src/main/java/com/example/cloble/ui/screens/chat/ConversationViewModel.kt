package com.example.cloble.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.VerifiedMatchDetail
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.result.CancelMatchResult
import com.example.cloble.domain.result.CompleteMatchResult
import com.example.cloble.domain.result.SendMessageResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationUiState(
    val matchDetail: VerifiedMatchDetail? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draftMessage: String = "",
    val isReadOnly: Boolean = false,
    val hasReviewed: Boolean = false,
    val canRatePartner: Boolean = false,
    val showCancelDialog: Boolean = false,
    val showCompleteDialog: Boolean = false,
    val meetingLocationInput: String = "",
    val isActionInProgress: Boolean = false,
    val errorMessage: String? = null,
    val currentUserId: Long? = null,
    val isLoading: Boolean = true
)

class ConversationViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository,
    private val chatRepository: ChatRepository,
    private val profileReviewRepository: ProfileReviewRepository
) : ViewModel() {

    val matchId: Long = savedStateHandle.get<Long>("matchId") ?: -1L

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = authRepository.observeLoggedInUserId().firstOrNull()
            if (userId != null && matchId > 0L) {
                _uiState.update { it.copy(currentUserId = userId) }

                chatRepository.markMessagesRead(matchId, userId)

                launch {
                    discoveryMatchRepository.observeMatchDetail(matchId, userId).collect { detail ->
                        _uiState.update { state ->
                            val isReadOnly = detail?.isReadOnly ?: false
                            val canRate = detail?.status == MatchStatus.COMPLETED && !state.hasReviewed
                            state.copy(
                                matchDetail = detail,
                                isReadOnly = isReadOnly,
                                canRatePartner = canRate,
                                isLoading = false
                            )
                        }
                    }
                }

                launch {
                    chatRepository.observeMessages(matchId, userId).collect { msgs ->
                        _uiState.update { it.copy(messages = msgs) }
                    }
                }

                launch {
                    profileReviewRepository.hasUserReviewedMatch(matchId, userId).collect { reviewed ->
                        _uiState.update { state ->
                            val canRate = state.matchDetail?.status == MatchStatus.COMPLETED && !reviewed
                            state.copy(
                                hasReviewed = reviewed,
                                canRatePartner = canRate
                            )
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onDraftChanged(text: String) {
        _uiState.update { it.copy(draftMessage = text) }
    }

    fun onMeetingLocationChanged(location: String) {
        _uiState.update { it.copy(meetingLocationInput = location) }
    }

    fun openCancelDialog() {
        _uiState.update { it.copy(showCancelDialog = true) }
    }

    fun dismissCancelDialog() {
        _uiState.update { it.copy(showCancelDialog = false) }
    }

    fun openCompleteDialog() {
        _uiState.update { it.copy(showCompleteDialog = true) }
    }

    fun dismissCompleteDialog() {
        _uiState.update { it.copy(showCompleteDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun sendMessage() {
        val text = _uiState.value.draftMessage.trim()
        if (text.isBlank()) return
        val userId = _uiState.value.currentUserId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(draftMessage = "") }
            when (val result = chatRepository.sendMessage(matchId, userId, text)) {
                is SendMessageResult.Success -> {

                }
                is SendMessageResult.ChatReadOnly -> {
                    _uiState.update { it.copy(errorMessage = "Ruang obrolan read-only karena pertukaran telah selesai atau dibatalkan.") }
                }
                is SendMessageResult.NotMatchParticipant -> {
                    _uiState.update { it.copy(errorMessage = "Anda bukan peserta pada Match ini.") }
                }
                is SendMessageResult.MatchNotFound -> {
                    _uiState.update { it.copy(errorMessage = "Match tidak ditemukan.") }
                }
                is SendMessageResult.EmptyMessage -> {}
                is SendMessageResult.DatabaseError -> {
                    _uiState.update { it.copy(errorMessage = "Gagal mengirim pesan: ${result.failure.message}") }
                }
            }
        }
    }

    fun confirmCancelMatch() {
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, showCancelDialog = false) }
            when (val result = discoveryMatchRepository.cancelMatch(matchId, userId)) {
                is CancelMatchResult.Success -> {
                    _uiState.update { it.copy(isActionInProgress = false) }
                }
                is CancelMatchResult.NotMatchParticipant -> {
                    _uiState.update { it.copy(isActionInProgress = false, errorMessage = "Hanya peserta yang dapat membatalkan pertukaran.") }
                }
                is CancelMatchResult.MatchNotActive -> {
                    _uiState.update { it.copy(isActionInProgress = false, errorMessage = "Pertukaran sudah tidak berstatus aktif.") }
                }
                is CancelMatchResult.MatchNotFound -> {
                    _uiState.update { it.copy(isActionInProgress = false, errorMessage = "Match tidak ditemukan.") }
                }
                is CancelMatchResult.DatabaseError -> {
                    _uiState.update { it.copy(isActionInProgress = false, errorMessage = "Gagal membatalkan Match: ${result.failure.message}") }
                }
            }
        }
    }

    fun confirmCompleteExchange() {
        val userId = _uiState.value.currentUserId ?: return
        val location = _uiState.value.meetingLocationInput.trim().ifBlank { null }
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, showCompleteDialog = false) }
            when (val result = discoveryMatchRepository.completeMatchExchange(matchId, userId, location)) {
                is CompleteMatchResult.Success -> {
                    _uiState.update { it.copy(isActionInProgress = false) }
                }
                is CompleteMatchResult.NotMatchParticipant -> {
                    _uiState.update { it.copy(isActionInProgress = false, errorMessage = "Hanya peserta yang dapat menyelesaikan pertukaran.") }
                }
                is CompleteMatchResult.MatchNotActive -> {
                    _uiState.update { it.copy(isActionInProgress = false, errorMessage = "Pertukaran sudah tidak aktif.") }
                }
                is CompleteMatchResult.ItemsNoLongerAvailable -> {
                    _uiState.update { it.copy(isActionInProgress = false, errorMessage = "Salah satu pakaian tidak lagi tersedia.") }
                }
                is CompleteMatchResult.MatchNotFound -> {
                    _uiState.update { it.copy(isActionInProgress = false, errorMessage = "Match tidak ditemukan.") }
                }
                is CompleteMatchResult.DatabaseError -> {
                    _uiState.update { it.copy(isActionInProgress = false, errorMessage = "Gagal menyelesaikan pertukaran: ${result.failure.message}") }
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication
                val savedStateHandle = createSavedStateHandle()
                ConversationViewModel(
                    savedStateHandle = savedStateHandle,
                    authRepository = app.container.authRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository,
                    chatRepository = app.container.chatRepository,
                    profileReviewRepository = app.container.profileReviewRepository
                )
            }
        }
    }
}
