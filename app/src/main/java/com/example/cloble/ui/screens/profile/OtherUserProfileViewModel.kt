package com.example.cloble.ui.screens.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.model.UserReview
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.DeclineInterestResult
import com.example.cloble.domain.result.RespondInterestResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OtherUserProfileUiState(
    val targetUserId: Long = -1L,
    val interestId: Long = -1L,
    val isReplyingToInterest: Boolean = false,
    val profile: UserProfile? = null,
    val reviews: List<UserReview> = emptyList(),
    val wardrobeItems: List<ClothingItem> = emptyList(),
    val selectedItemId: Long? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isDeclining: Boolean = false,
    val showDeclineDialog: Boolean = false,
    val errorMessage: String? = null
)

sealed interface OtherUserProfileEvent {
    data class MatchCreated(val matchId: Long) : OtherUserProfileEvent
    data object ReplyRejected : OtherUserProfileEvent
}

class OtherUserProfileViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val profileReviewRepository: ProfileReviewRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository
) : ViewModel() {

    private val targetUserId: Long = savedStateHandle.get<Long>("userId") ?: -1L
    private val interestId: Long = savedStateHandle.get<Long>("replyToInterestId")
        ?: savedStateHandle.get<Long>("interestId")
        ?: -1L
    private val isReplying: Boolean = interestId > 0L

    private val _uiState = MutableStateFlow(
        OtherUserProfileUiState(
            targetUserId = targetUserId,
            interestId = interestId,
            isReplyingToInterest = isReplying
        )
    )
    val uiState: StateFlow<OtherUserProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OtherUserProfileEvent>()
    val events: SharedFlow<OtherUserProfileEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        loadData()
    }

    private fun loadData(showLoading: Boolean = true) {
        loadJob?.cancel()
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true) }
        }
        loadJob = viewModelScope.launch {
            combine(
                profileReviewRepository.observeUserProfile(targetUserId),
                profileReviewRepository.observeUserReviews(targetUserId),
                wardrobeRepository.observeUserPublicWardrobe(targetUserId)
            ) { profile, reviews, itemSummaries ->
                val eligibleItems = if (isReplying) {
                    itemSummaries
                        .filter { !it.isInActiveMatch && it.item.isBarterAllowed && it.item.status == ItemStatus.AVAILABLE }
                        .map { it.item }
                } else {
                    itemSummaries.map { it.item }
                }
                Triple(profile, reviews, eligibleItems)
            }.collect { (profile, reviews, eligibleItems) ->
                _uiState.update { current ->
                    val keptSelection = current.selectedItemId?.takeIf { selectedId ->
                        eligibleItems.any { it.id == selectedId }
                    }
                    current.copy(
                        profile = profile,
                        reviews = reviews,
                        wardrobeItems = eligibleItems,
                        selectedItemId = keptSelection,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun retry() {
        _uiState.update { it.copy(errorMessage = null) }
        loadData(showLoading = true)
    }

    private fun refreshPublicWardrobe() {
        loadData(showLoading = false)
    }

    fun selectItem(itemId: Long) {
        _uiState.update { current ->
            val nextSelected = if (current.selectedItemId == itemId) null else itemId
            current.copy(selectedItemId = nextSelected, errorMessage = null)
        }
    }

    fun acceptInterestAndCreateMatch() {
        val state = _uiState.value
        if (state.isSubmitting || state.isDeclining) return
        val chosenItemId = state.selectedItemId ?: return

        viewModelScope.launch {
            val currentUserId = authRepository.observeLoggedInUserId().firstOrNull() ?: return@launch
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

            val result = discoveryMatchRepository.respondToInterestAndCreateMatch(
                actorUserId = currentUserId,
                interestId = interestId,
                chosenSenderItemId = chosenItemId
            )

            when (result) {
                is RespondInterestResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _events.emit(OtherUserProfileEvent.MatchCreated(result.matchId))
                }
                is RespondInterestResult.InterestAlreadyProcessedOrInvalid -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "Tawaran interest ini sudah tidak berlaku atau sudah direspons."
                        )
                    }
                    refreshPublicWardrobe()
                }
                is RespondInterestResult.TargetItemNotFound -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "Pakaian Anda yang ditawarkan sudah tidak ditemukan."
                        )
                    }
                    refreshPublicWardrobe()
                }
                is RespondInterestResult.TargetItemUnavailable -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "Pakaian Anda yang ditawarkan sudah tidak tersedia untuk barter."
                        )
                    }
                    refreshPublicWardrobe()
                }
                is RespondInterestResult.ChosenItemNotFound -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            selectedItemId = null,
                            errorMessage = "Pakaian lawan yang dipilih sudah dihapus atau tidak ditemukan."
                        )
                    }
                    refreshPublicWardrobe()
                }
                is RespondInterestResult.ChosenItemUnavailable -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            selectedItemId = null,
                            errorMessage = "Pakaian lawan yang dipilih sudah tidak tersedia untuk barter."
                        )
                    }
                    refreshPublicWardrobe()
                }
                is RespondInterestResult.BarterNotAllowed -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            selectedItemId = null,
                            errorMessage = "Salah satu pakaian saat ini tidak diizinkan untuk barter."
                        )
                    }
                    refreshPublicWardrobe()
                }
                is RespondInterestResult.InvalidItemOwnership -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "Kepemilikan pakaian tidak valid untuk tawaran ini."
                        )
                    }
                }
                is RespondInterestResult.ItemAlreadyInActiveMatch -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            selectedItemId = null,
                            errorMessage = "Pakaian yang dipilih saat ini sedang terikat dalam Match aktif lain."
                        )
                    }
                    refreshPublicWardrobe()
                }
                is RespondInterestResult.PairAlreadyMatched -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "Pasangan pakaian ini sudah memiliki pertukaran aktif atau sudah selesai ditukar."
                        )
                    }
                }
                is RespondInterestResult.DatabaseError -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "Gagal memproses tawaran barter. Periksa koneksi internet Anda dan coba lagi."
                        )
                    }
                }
            }
        }
    }

    fun openDeclineDialog() {
        _uiState.update { it.copy(showDeclineDialog = true) }
    }

    fun dismissDeclineDialog() {
        _uiState.update { it.copy(showDeclineDialog = false) }
    }

    fun declineInterest() {
        if (!isReplying || interestId <= 0L) return
        val state = _uiState.value
        if (state.isSubmitting || state.isDeclining) return
        viewModelScope.launch {
            val currentUserId = authRepository.observeLoggedInUserId().firstOrNull() ?: return@launch
            _uiState.update { it.copy(isDeclining = true, showDeclineDialog = false, errorMessage = null) }
            when (discoveryMatchRepository.declineIncomingInterest(currentUserId, interestId)) {
                is DeclineInterestResult.Success -> {
                    _uiState.update { it.copy(isDeclining = false) }
                    _events.emit(OtherUserProfileEvent.ReplyRejected)
                }
                is DeclineInterestResult.InterestAlreadyProcessedOrInvalid -> {
                    _uiState.update {
                        it.copy(
                            isDeclining = false,
                            errorMessage = "Tawaran interest ini sudah tidak berlaku atau sudah direspons."
                        )
                    }
                }
                is DeclineInterestResult.DatabaseError -> {
                    _uiState.update {
                        it.copy(
                            isDeclining = false,
                            errorMessage = "Gagal menolak tawaran. Silakan coba lagi."
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
                val handle = createSavedStateHandle()
                OtherUserProfileViewModel(
                    savedStateHandle = handle,
                    authRepository = app.container.authRepository,
                    profileReviewRepository = app.container.profileReviewRepository,
                    wardrobeRepository = app.container.wardrobeRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository
                )
            }
        }
    }
}
