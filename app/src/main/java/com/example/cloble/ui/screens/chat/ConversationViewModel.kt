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
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.VerifiedMatchDetail
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.result.CancelMatchResult
import com.example.cloble.domain.result.CompleteMatchResult
import com.example.cloble.domain.result.SendMessageResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationUiState(
    val activeMatchId: Long = -1L,
    val matchDetail: VerifiedMatchDetail? = null,
    val partnerMatches: List<ChatThreadSummary> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val draftMessage: String = "",
    val isReadOnly: Boolean = false,
    val hasReviewed: Boolean = false,
    val isReviewStatusLoaded: Boolean = false,
    val canRatePartner: Boolean = false,
    val showCancelDialog: Boolean = false,
    val showCompleteDialog: Boolean = false,
    val meetingLocationInput: String = "",
    val isSendingMessage: Boolean = false,
    val isActionInProgress: Boolean = false,
    val errorMessage: String? = null,
    val currentUserId: Long? = null,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository,
    private val chatRepository: ChatRepository,
    private val profileReviewRepository: ProfileReviewRepository
) : ViewModel() {

    val matchId: Long = savedStateHandle.get<Long>("matchId") ?: -1L

    private val selectedMatchIdFlow = MutableStateFlow(matchId)
    private val _uiState = MutableStateFlow(ConversationUiState(activeMatchId = matchId))
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var partnerHistoryJob: Job? = null

    init {
        viewModelScope.launch {
            val userId = authRepository.observeLoggedInUserId().firstOrNull()
            if (userId != null && matchId > 0L) {
                _uiState.update { it.copy(currentUserId = userId) }

                launch {
                    selectedMatchIdFlow.collect { activeId ->
                        if (activeId > 0L) {
                            chatRepository.markMessagesRead(activeId, userId)
                        }
                    }
                }

                launch {
                    selectedMatchIdFlow
                        .flatMapLatest { activeId ->
                            if (activeId > 0L) {
                                discoveryMatchRepository.observeMatchDetail(activeId, userId)
                                    .map { detail -> activeId to detail }
                            } else {
                                flowOf(-1L to null)
                            }
                        }
                        .collect { (emittedMatchId, detail) ->
                            _uiState.update { state ->
                                if (state.activeMatchId != emittedMatchId) {
                                    state
                                } else {
                                    val isReadOnly = detail?.isReadOnly ?: false
                                    val canRate = detail?.status == MatchStatus.COMPLETED &&
                                        state.isReviewStatusLoaded &&
                                        !state.hasReviewed
                                    state.copy(
                                        matchDetail = detail,
                                        isReadOnly = isReadOnly,
                                        canRatePartner = canRate,
                                        isLoading = false
                                    )
                                }
                            }
                            if (detail != null && partnerHistoryJob == null) {
                                partnerHistoryJob = launch {
                                    chatRepository.observePartnerMatches(userId, detail.partnerUserId)
                                        .collect { matches ->
                                            _uiState.update { it.copy(partnerMatches = matches) }
                                        }
                                }
                            }
                        }
                }

                val lastAutoMarkedMessageIdByMatch = mutableMapOf<Long, Long>()

                launch {
                    selectedMatchIdFlow
                        .flatMapLatest { activeId ->
                            if (activeId > 0L) {
                                chatRepository.observeMessages(activeId, userId).map { msgs ->
                                    activeId to msgs
                                }
                            } else {
                                flowOf(-1L to emptyList())
                            }
                        }
                        .collect { (activeId, msgs) ->
                            _uiState.update { state ->
                                if (state.activeMatchId != activeId) {
                                    state
                                } else {
                                    state.copy(messages = msgs)
                                }
                            }
                            if (activeId > 0L && _uiState.value.activeMatchId == activeId) {
                                val maxUnreadIncomingId = msgs
                                    .filter { !it.isFromMe && !it.isRead }
                                    .maxOfOrNull { it.id }
                                val lastMarkedId = lastAutoMarkedMessageIdByMatch[activeId]
                                if (maxUnreadIncomingId != null &&
                                    (lastMarkedId == null || maxUnreadIncomingId > lastMarkedId)
                                ) {
                                    lastAutoMarkedMessageIdByMatch[activeId] = maxUnreadIncomingId
                                    launch {
                                        chatRepository.markMessagesRead(activeId, userId)
                                    }
                                }
                            }
                        }
                }

                launch {
                    selectedMatchIdFlow
                        .flatMapLatest { activeId ->
                            if (activeId > 0L) {
                                profileReviewRepository.hasUserReviewedMatch(activeId, userId)
                                    .map { reviewed -> activeId to reviewed }
                            } else {
                                flowOf(-1L to false)
                            }
                        }
                        .collect { (emittedMatchId, reviewed) ->
                            _uiState.update { state ->
                                if (state.activeMatchId != emittedMatchId) {
                                    state
                                } else {
                                    val canRate = state.matchDetail?.status == MatchStatus.COMPLETED && !reviewed
                                    state.copy(
                                        hasReviewed = reviewed,
                                        isReviewStatusLoaded = true,
                                        canRatePartner = canRate
                                    )
                                }
                            }
                        }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectMatch(targetMatchId: Long) {
        val snapshot = _uiState.value
        if (targetMatchId <= 0L ||
            targetMatchId == selectedMatchIdFlow.value ||
            snapshot.isSendingMessage ||
            snapshot.isActionInProgress
        ) return
        _uiState.update {
            it.copy(
                activeMatchId = targetMatchId,
                matchDetail = null,
                messages = emptyList(),
                draftMessage = "",
                isReadOnly = false,
                isLoading = true,
                meetingLocationInput = "",
                hasReviewed = false,
                isReviewStatusLoaded = false,
                canRatePartner = false,
                errorMessage = null,
                showCancelDialog = false,
                showCompleteDialog = false
            )
        }
        selectedMatchIdFlow.value = targetMatchId
        savedStateHandle["matchId"] = targetMatchId
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
        val snapshot = _uiState.value
        if (snapshot.isSendingMessage || snapshot.isReadOnly) return
        val text = snapshot.draftMessage.trim()
        if (text.isBlank()) return
        val userId = snapshot.currentUserId ?: return
        val activeId = snapshot.activeMatchId.takeIf { it > 0L } ?: matchId

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingMessage = true, draftMessage = "", errorMessage = null) }
            when (chatRepository.sendMessage(activeId, userId, text)) {
                is SendMessageResult.Success -> {
                    _uiState.update { it.copy(isSendingMessage = false) }
                }
                is SendMessageResult.ChatReadOnly -> {
                    _uiState.update { state ->
                        state.copy(
                            isSendingMessage = false,
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Ruang obrolan read-only karena pertukaran telah selesai atau dibatalkan."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
                }
                is SendMessageResult.NotMatchParticipant -> {
                    _uiState.update { state ->
                        state.copy(
                            isSendingMessage = false,
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Anda bukan peserta pada Match ini."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
                }
                is SendMessageResult.MatchNotFound -> {
                    _uiState.update { state ->
                        state.copy(
                            isSendingMessage = false,
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Match tidak ditemukan."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
                }
                is SendMessageResult.EmptyMessage -> {
                    _uiState.update { it.copy(isSendingMessage = false) }
                }
                is SendMessageResult.DatabaseError -> {
                    _uiState.update { state ->
                        state.copy(
                            isSendingMessage = false,
                            draftMessage = if (state.activeMatchId == activeId && state.draftMessage.isBlank()) {
                                text
                            } else {
                                state.draftMessage
                            },
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Gagal mengirim pesan. Periksa koneksi internet Anda dan coba lagi."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
                }
            }
        }
    }

    fun confirmCancelMatch() {
        if (_uiState.value.isActionInProgress) return
        val userId = _uiState.value.currentUserId ?: return
        val activeId = _uiState.value.activeMatchId.takeIf { it > 0L } ?: matchId
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, showCancelDialog = false, errorMessage = null) }
            when (discoveryMatchRepository.cancelMatch(activeId, userId)) {
                is CancelMatchResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            isActionInProgress = false,
                            errorMessage = if (state.activeMatchId == activeId) null else state.errorMessage
                        )
                    }
                }
                is CancelMatchResult.NotMatchParticipant -> {
                    _uiState.update { state ->
                        state.copy(
                            isActionInProgress = false,
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Hanya peserta yang dapat membatalkan pertukaran."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
                }
                is CancelMatchResult.MatchNotActive -> {
                    _uiState.update { state ->
                        state.copy(
                            isActionInProgress = false,
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Pertukaran sudah tidak berstatus aktif."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
                }
                is CancelMatchResult.MatchNotFound -> {
                    _uiState.update { state ->
                        state.copy(
                            isActionInProgress = false,
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Match tidak ditemukan."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
                }
                is CancelMatchResult.DatabaseError -> {
                    _uiState.update { state ->
                        state.copy(
                            isActionInProgress = false,
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Gagal membatalkan Match. Periksa koneksi internet Anda dan coba lagi."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
                }
            }
        }
    }

    fun confirmCompleteExchange() {
        if (_uiState.value.isActionInProgress) return
        val userId = _uiState.value.currentUserId ?: return
        val activeId = _uiState.value.activeMatchId.takeIf { it > 0L } ?: matchId
        val location = _uiState.value.meetingLocationInput.trim().ifBlank { null }
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, showCompleteDialog = false, errorMessage = null) }
            when (discoveryMatchRepository.completeMatchExchange(activeId, userId, location)) {
                is CompleteMatchResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            isActionInProgress = false,
                            meetingLocationInput = if (state.activeMatchId == activeId) "" else state.meetingLocationInput,
                            errorMessage = if (state.activeMatchId == activeId) null else state.errorMessage
                        )
                    }
                }
                is CompleteMatchResult.NotMatchParticipant -> {
                    _uiState.update { state ->
                        state.copy(
                            isActionInProgress = false,
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Hanya peserta yang dapat menyelesaikan pertukaran."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
                }
                is CompleteMatchResult.MatchNotActive -> {
                    _uiState.update { state ->
                        state.copy(
                            isActionInProgress = false,
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Pertukaran sudah tidak aktif."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
                }
                is CompleteMatchResult.ItemsNoLongerAvailable -> {
                    _uiState.update { state ->
                        state.copy(
                            isActionInProgress = false,
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Salah satu pakaian tidak lagi tersedia."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
                }
                is CompleteMatchResult.MatchNotFound -> {
                    _uiState.update { state ->
                        state.copy(
                            isActionInProgress = false,
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Match tidak ditemukan."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
                }
                is CompleteMatchResult.DatabaseError -> {
                    _uiState.update { state ->
                        state.copy(
                            isActionInProgress = false,
                            errorMessage = if (state.activeMatchId == activeId) {
                                "Gagal menyelesaikan pertukaran. Periksa koneksi internet Anda dan coba lagi."
                            } else {
                                state.errorMessage
                            }
                        )
                    }
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
