package com.example.cloble.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.model.UserReview
import com.example.cloble.domain.model.WardrobeItemSummary
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.repository.WardrobeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyProfileUiState(
    val userProfile: UserProfile? = null,
    val reviews: List<UserReview> = emptyList(),
    val wardrobePreview: List<WardrobeItemSummary> = emptyList(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class MyProfileViewModel(
    private val authRepository: AuthRepository,
    private val profileReviewRepository: ProfileReviewRepository,
    private val wardrobeRepository: WardrobeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyProfileUiState())
    val uiState: StateFlow<MyProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeCurrentUser().collect { user ->
                _uiState.update { current ->
                    current.copy(
                        userProfile = user,
                        isLoading = false
                    )
                }
            }
        }

        viewModelScope.launch {
            authRepository.observeLoggedInUserId().flatMapLatest { userId ->
                if (userId == null) flowOf(emptyList())
                else profileReviewRepository.observeUserReviews(userId)
            }.collect { reviewsList ->
                _uiState.update { it.copy(reviews = reviewsList) }
            }
        }

        viewModelScope.launch {
            authRepository.observeLoggedInUserId().flatMapLatest { userId ->
                if (userId == null) flowOf(emptyList())
                else wardrobeRepository.observeMyWardrobe(userId)
            }.collect { items ->
                _uiState.update { it.copy(wardrobePreview = items.take(4)) }
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onSuccess()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication)
                MyProfileViewModel(
                    authRepository = app.container.authRepository,
                    profileReviewRepository = app.container.profileReviewRepository,
                    wardrobeRepository = app.container.wardrobeRepository
                )
            }
        }
    }
}
