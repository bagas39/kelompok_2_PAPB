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
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.model.UserReview
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.RespondInterestResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val profile = profileReviewRepository.observeUserProfile(targetUserId).firstOrNull()
            val reviews = profileReviewRepository.observeUserReviews(targetUserId).firstOrNull() ?: emptyList()

            wardrobeRepository.observeUserPublicWardrobe(targetUserId).collect { itemSummaries ->
                val items = itemSummaries.map { it.item }
                val eligibleItems = if (isReplying) {
                    items.filter { it.isBarterAllowed && it.status == com.example.cloble.domain.model.ItemStatus.AVAILABLE }
                } else {
                    items
                }
                _uiState.update {
                    it.copy(
                        profile = profile,
                        reviews = reviews,
                        wardrobeItems = eligibleItems,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectItem(itemId: Long) {
        _uiState.update { current ->
            val nextSelected = if (current.selectedItemId == itemId) null else itemId
            current.copy(selectedItemId = nextSelected, errorMessage = null)
        }
    }

    fun acceptInterestAndCreateMatch() {
        val state = _uiState.value
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
                }
                is RespondInterestResult.ItemAlreadyInActiveMatch -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "Pakaian yang dipilih saat ini sedang terikat dalam Match aktif lain."
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "Gagal memproses tawaran barter. Silakan coba lagi."
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
