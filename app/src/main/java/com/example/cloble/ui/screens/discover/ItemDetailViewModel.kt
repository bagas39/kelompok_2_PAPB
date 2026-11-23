package com.example.cloble.ui.screens.discover

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.CancelInterestResult
import com.example.cloble.domain.result.SendInterestResult
import com.example.cloble.domain.util.DistanceCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemDetailUiState(
    val itemId: Long = -1L,
    val item: ClothingItem? = null,
    val owner: UserProfile? = null,
    val isOwnItem: Boolean = false,
    val distanceKm: Int? = null,
    val existingInterestId: Long? = null,
    val existingInterestStatus: InterestStatus? = null,
    val relatedMatchId: Long? = null,
    val relatedMatchStatus: MatchStatus? = null,
    val canReofferAfterCancelled: Boolean = false,
    val isLockedByOtherMatch: Boolean = false,
    val isItemUnavailable: Boolean = false,
    val isLoading: Boolean = true,
    val isSendingInterest: Boolean = false,
    val isCancellingInterest: Boolean = false,
    val interestSentSuccess: Boolean = false,
    val actionMessage: String? = null,
    val errorMessage: String? = null,
    val requiresBarterItemCta: Boolean = false
) {
    val canReofferAfterInvalidated: Boolean
        get() = existingInterestStatus == InterestStatus.INVALIDATED &&
            relatedMatchId == null &&
            !isLockedByOtherMatch &&
            !isItemUnavailable
}

class ItemDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val profileReviewRepository: ProfileReviewRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository
) : ViewModel() {

    private val rawItemId: Long = savedStateHandle.get<Long>("itemId") ?: -1L

    private val _uiState = MutableStateFlow(ItemDetailUiState(itemId = rawItemId))
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()
    private var loadJob: kotlinx.coroutines.Job? = null
    private var hasObservedInitialResume = false

    init {
        loadDetail()
    }

    fun onScreenResumed() {
        if (!hasObservedInitialResume) {
            hasObservedInitialResume = true
            return
        }
        refreshDetail()
    }

    fun refreshDetail() {
        loadDetail()
    }

    private fun loadDetail() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val currentUserId = authRepository.observeLoggedInUserId().firstOrNull()
            val viewerProfile = if (currentUserId != null) {
                profileReviewRepository.observeUserProfile(currentUserId).firstOrNull()
            } else {
                null
            }
            val item = wardrobeRepository.getClothingItem(rawItemId)
            if (item != null) {
                val owner = profileReviewRepository.observeUserProfile(item.ownerId).firstOrNull()
                val isOwn = currentUserId != null && currentUserId == item.ownerId

                val computedDistanceKm = if (!isOwn && viewerProfile != null && owner != null) {
                    owner.distanceKm ?: run {
                        val (vLat, vLon) = DistanceCalculator.resolveEffectiveCoordinates(
                            viewerProfile.city,
                            viewerProfile.latitude,
                            viewerProfile.longitude
                        )
                        val (oLat, oLon) = DistanceCalculator.resolveEffectiveCoordinates(
                            owner.city,
                            owner.latitude,
                            owner.longitude
                        )
                        DistanceCalculator.calculateKm(vLat, vLon, oLat, oLon)
                    }
                } else {
                    null
                }

                val outgoing = if (!isOwn && currentUserId != null) {
                    discoveryMatchRepository.observeOutgoingInterests(currentUserId).firstOrNull()
                        ?.firstOrNull { it.targetItemId == item.id }
                } else {
                    null
                }

                val isItemLocked = wardrobeRepository.isItemLocked(item.id)
                val lockedByOther = !isOwn && (
                    outgoing?.isLockedByOtherMatch == true ||
                        ((outgoing?.matchId == null || outgoing.matchStatus == MatchStatus.CANCELLED) && isItemLocked)
                    )
                val itemUnavailable = !isOwn && (
                    item.status != ItemStatus.AVAILABLE ||
                        !item.isBarterAllowed ||
                        isItemLocked
                    )
                val canReofferAfterCancelled = outgoing?.matchStatus == MatchStatus.CANCELLED &&
                    !lockedByOther &&
                    !itemUnavailable
                val shouldClearBarterPrompt = _uiState.value.requiresBarterItemCta &&
                    currentUserId != null &&
                    wardrobeRepository.countEligibleBarterItems(currentUserId) > 0

                _uiState.update {
                    val shouldKeepError = !shouldClearBarterPrompt &&
                        (it.requiresBarterItemCta || it.errorMessage == "Minat sudah diproses oleh pemilik.")
                    it.copy(
                        item = item,
                        owner = owner,
                        isOwnItem = isOwn,
                        distanceKm = computedDistanceKm,
                        existingInterestId = outgoing?.interestId,
                        existingInterestStatus = outgoing?.interestStatus,
                        relatedMatchId = outgoing?.matchId,
                        relatedMatchStatus = outgoing?.matchStatus,
                        canReofferAfterCancelled = canReofferAfterCancelled,
                        isLockedByOtherMatch = lockedByOther,
                        isItemUnavailable = itemUnavailable,
                        interestSentSuccess = outgoing?.interestStatus == InterestStatus.PENDING,
                        requiresBarterItemCta = if (shouldClearBarterPrompt) false else it.requiresBarterItemCta,
                        errorMessage = if (shouldKeepError) it.errorMessage else null,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        item = null,
                        owner = null,
                        isOwnItem = false,
                        distanceKm = null,
                        existingInterestId = null,
                        existingInterestStatus = null,
                        relatedMatchId = null,
                        relatedMatchStatus = null,
                        canReofferAfterCancelled = false,
                        isLockedByOtherMatch = false,
                        isItemUnavailable = true,
                        interestSentSuccess = false,
                        requiresBarterItemCta = false,
                        isLoading = false,
                        errorMessage = "Pakaian tidak ditemukan."
                    )
                }
            }
        }
    }

    fun sendInterest() {
        val state = _uiState.value
        val item = state.item ?: return
        val canReoffer = state.canReofferAfterCancelled || state.canReofferAfterInvalidated
        if (state.isSendingInterest ||
            state.isLockedByOtherMatch ||
            state.isItemUnavailable ||
            (state.existingInterestStatus != null && !canReoffer)
        ) return

        viewModelScope.launch {
            val currentUserId = authRepository.observeLoggedInUserId().firstOrNull() ?: return@launch
            _uiState.update { it.copy(isSendingInterest = true, errorMessage = null, actionMessage = null) }

            val result = discoveryMatchRepository.sendInterest(currentUserId, item.id)
            when (result) {
                is SendInterestResult.Success -> {
                    val outgoingAfter = discoveryMatchRepository.observeOutgoingInterests(currentUserId)
                        .firstOrNull()
                        ?.firstOrNull { it.targetItemId == item.id }
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            interestSentSuccess = result.matchId == null,
                            existingInterestId = result.interestId,
                            existingInterestStatus = if (result.matchId != null) {
                                InterestStatus.RESOLVED_BY_MATCH
                            } else {
                                InterestStatus.PENDING
                            },
                            relatedMatchId = result.matchId ?: outgoingAfter?.matchId,
                            relatedMatchStatus = if (result.matchId != null) MatchStatus.ACTIVE else outgoingAfter?.matchStatus,
                            canReofferAfterCancelled = false,
                            requiresBarterItemCta = false,
                            actionMessage = if (result.matchId != null) {
                                "Match langsung terbentuk karena pemilik juga menyukai pakaian Anda!"
                            } else {
                                null
                            }
                        )
                    }
                }
                is SendInterestResult.RequiresBarterableItemInWardrobe -> {
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            errorMessage = "Anda belum memiliki pakaian yang diizinkan untuk barter di Lemari. Tambahkan pakaian barter terlebih dahulu.",
                            requiresBarterItemCta = true
                        )
                    }
                }
                is SendInterestResult.AlreadyInterested -> {
                    val latest = discoveryMatchRepository.observeOutgoingInterests(currentUserId)
                        .firstOrNull()?.firstOrNull {
                            it.targetItemId == item.id && it.interestStatus == result.existingStatus
                        }
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            existingInterestId = latest?.interestId,
                            existingInterestStatus = result.existingStatus,
                            relatedMatchId = latest?.matchId,
                            relatedMatchStatus = latest?.matchStatus,
                            canReofferAfterCancelled = false,
                            interestSentSuccess = result.existingStatus == InterestStatus.PENDING,
                            errorMessage = when (result.existingStatus) {
                                InterestStatus.PENDING -> "Tawaran untuk pakaian ini masih menunggu respons pemilik."
                                InterestStatus.DECLINED -> "Tawaran sebelumnya untuk pakaian ini telah ditolak."
                                else -> "Status tawaran sudah berubah. Silakan muat ulang detail pakaian."
                            },
                            requiresBarterItemCta = false
                        )
                    }
                }
                is SendInterestResult.TargetItemNotEligible -> {
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            isItemUnavailable = true,
                            canReofferAfterCancelled = false,
                            errorMessage = "Pakaian ini sudah tidak tersedia untuk dibarter.",
                            requiresBarterItemCta = false
                        )
                    }
                }
                is SendInterestResult.CannotInterestOwnItem -> {
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            errorMessage = "Ini adalah pakaian milik Anda sendiri.",
                            requiresBarterItemCta = false
                        )
                    }
                }
                is SendInterestResult.PairAlreadyMatched -> {
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            errorMessage = "Pasangan pakaian ini sudah memiliki pertukaran aktif atau sudah selesai ditukar.",
                            requiresBarterItemCta = false
                        )
                    }
                }
                is SendInterestResult.TargetItemNotFound -> {
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            isItemUnavailable = true,
                            canReofferAfterCancelled = false,
                            errorMessage = "Pakaian tidak ditemukan atau sudah dihapus.",
                            requiresBarterItemCta = false
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            isSendingInterest = false,
                            errorMessage = "Gagal mengirim interest. Silakan coba lagi.",
                            requiresBarterItemCta = false
                        )
                    }
                }
            }
        }
    }

    fun cancelPendingInterest() {
        val state = _uiState.value
        if (state.isCancellingInterest || state.isSendingInterest) return
        val interestId = state.existingInterestId ?: return
        viewModelScope.launch {
            val currentUserId = authRepository.observeLoggedInUserId().firstOrNull() ?: return@launch
            _uiState.update { it.copy(isCancellingInterest = true, errorMessage = null, actionMessage = null) }
            when (val res = discoveryMatchRepository.cancelOutgoingInterest(currentUserId, interestId)) {
                is CancelInterestResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isCancellingInterest = false,
                            existingInterestId = null,
                            existingInterestStatus = null,
                            interestSentSuccess = false,
                            actionMessage = "Minat berhasil dibatalkan. Pakaian kembali tersedia di Jelajah."
                        )
                    }
                }
                is CancelInterestResult.InterestAlreadyProcessed -> {
                    loadDetail()
                    _uiState.update {
                        it.copy(
                            isCancellingInterest = false,
                            errorMessage = "Minat sudah diproses oleh pemilik."
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            isCancellingInterest = false,
                            errorMessage = "Gagal membatalkan minat."
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
                ItemDetailViewModel(
                    savedStateHandle = handle,
                    authRepository = app.container.authRepository,
                    wardrobeRepository = app.container.wardrobeRepository,
                    profileReviewRepository = app.container.profileReviewRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository
                )
            }
        }
    }
}
