package com.example.cloble.ui.screens.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.VerifiedMatchDetail
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.result.ReviewSubmitResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RateUserUiState(
    val matchDetail: VerifiedMatchDetail? = null,
    val selectedRating: Int = 0,
    val comment: String = "",
    val alreadyReviewed: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val currentUserId: Long? = null,
    val isLoading: Boolean = true
)

class RateUserViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository,
    private val profileReviewRepository: ProfileReviewRepository
) : ViewModel() {

    val matchId: Long = savedStateHandle.get<Long>("matchId") ?: -1L

    private val _uiState = MutableStateFlow(RateUserUiState())
    val uiState: StateFlow<RateUserUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = authRepository.observeLoggedInUserId().firstOrNull()
            if (userId != null && matchId > 0L) {
                _uiState.update { it.copy(currentUserId = userId) }

                launch {
                    discoveryMatchRepository.observeMatchDetail(matchId, userId).collect { detail ->
                        _uiState.update { it.copy(matchDetail = detail, isLoading = false) }
                    }
                }

                launch {
                    profileReviewRepository.hasUserReviewedMatch(matchId, userId).collect { reviewed ->
                        _uiState.update { it.copy(alreadyReviewed = reviewed) }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectRating(rating: Int) {
        if (!_uiState.value.alreadyReviewed && !_uiState.value.isSubmitting) {
            _uiState.update { it.copy(selectedRating = rating, errorMessage = null) }
        }
    }

    fun onCommentChanged(comment: String) {
        if (!_uiState.value.alreadyReviewed && !_uiState.value.isSubmitting) {
            _uiState.update { it.copy(comment = comment, errorMessage = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun submitReview() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val userId = state.currentUserId ?: return

        if (state.alreadyReviewed) {
            _uiState.update { it.copy(errorMessage = "Anda sudah memberikan ulasan untuk pertukaran ini.") }
            return
        }

        if (state.selectedRating !in 1..5) {
            _uiState.update { it.copy(errorMessage = "Pilih nilai rating 1 sampai 5 bintang sebelum mengirim ulasan.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = profileReviewRepository.submitReview(
                matchId = matchId,
                reviewerId = userId,
                rating = state.selectedRating,
                comment = state.comment.trim()
            )

            when (result) {
                is ReviewSubmitResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, isSuccess = true, alreadyReviewed = true) }
                }
                is ReviewSubmitResult.AlreadyReviewed -> {
                    _uiState.update { it.copy(isSubmitting = false, alreadyReviewed = true, errorMessage = "Anda sudah mengirimkan ulasan.") }
                }
                is ReviewSubmitResult.MatchNotCompleted -> {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = "Ulasan hanya dapat dikirim untuk pertukaran yang sudah selesai.") }
                }
                is ReviewSubmitResult.NotMatchParticipant -> {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = "Hanya peserta pertukaran yang dapat memberikan ulasan.") }
                }
                is ReviewSubmitResult.MatchNotFound -> {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = "Data Match tidak ditemukan.") }
                }
                is ReviewSubmitResult.InvalidRating -> {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = "Rating harus antara 1 sampai 5 bintang.") }
                }
                is ReviewSubmitResult.DatabaseError -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "Gagal menyimpan ulasan. Periksa koneksi internet Anda dan coba lagi."
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
                RateUserViewModel(
                    savedStateHandle = savedStateHandle,
                    authRepository = app.container.authRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository,
                    profileReviewRepository = app.container.profileReviewRepository
                )
            }
        }
    }
}
