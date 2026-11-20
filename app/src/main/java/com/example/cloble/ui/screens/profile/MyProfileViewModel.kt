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
import com.example.cloble.domain.result.ResetPasswordResult
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
    val isLoading: Boolean = true,
    val showChangePasswordDialog: Boolean = false,
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isUpdatingPassword: Boolean = false,
    val passwordErrorMessage: String? = null,
    val passwordSuccessMessage: String? = null
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

    fun openChangePasswordDialog() {
        _uiState.update {
            it.copy(
                showChangePasswordDialog = true,
                newPassword = "",
                confirmPassword = "",
                passwordErrorMessage = null,
                passwordSuccessMessage = null
            )
        }
    }

    fun dismissChangePasswordDialog() {
        _uiState.update {
            it.copy(
                showChangePasswordDialog = false,
                isUpdatingPassword = false,
                passwordErrorMessage = null
            )
        }
    }

    fun onNewPasswordChanged(value: String) {
        _uiState.update { it.copy(newPassword = value, passwordErrorMessage = null) }
    }

    fun onConfirmPasswordChanged(value: String) {
        _uiState.update { it.copy(confirmPassword = value, passwordErrorMessage = null) }
    }

    fun submitChangePassword() {
        val state = _uiState.value
        val email = state.userProfile?.email ?: return
        if (state.newPassword.length < 6) {
            _uiState.update { it.copy(passwordErrorMessage = "Password baru minimal 6 karakter") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(passwordErrorMessage = "Konfirmasi password tidak cocok") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingPassword = true, passwordErrorMessage = null) }
            when (val res = authRepository.resetPassword(email, state.newPassword)) {
                is ResetPasswordResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingPassword = false,
                            showChangePasswordDialog = false,
                            newPassword = "",
                            confirmPassword = "",
                            passwordSuccessMessage = "Password akun berhasil diubah."
                        )
                    }
                }
                is ResetPasswordResult.ValidationFailed -> {
                    _uiState.update { it.copy(isUpdatingPassword = false, passwordErrorMessage = res.message) }
                }
                is ResetPasswordResult.EmailNotFound -> {
                    _uiState.update { it.copy(isUpdatingPassword = false, passwordErrorMessage = "Akun tidak ditemukan.") }
                }
                is ResetPasswordResult.DatabaseError -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingPassword = false,
                            passwordErrorMessage = res.failure.message ?: "Gagal mengubah password."
                        )
                    }
                }
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
