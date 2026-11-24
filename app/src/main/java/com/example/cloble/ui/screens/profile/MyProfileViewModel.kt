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
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.ChangePasswordResult
import com.example.cloble.domain.util.PasswordPolicy
import com.example.cloble.ui.theme.AppThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyProfileUiState(
    val userProfile: UserProfile? = null,
    val reviews: List<UserReview> = emptyList(),
    val wardrobePreview: List<WardrobeItemSummary> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val isLoading: Boolean = true,
    val showChangePasswordDialog: Boolean = false,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isCurrentPasswordVisible: Boolean = false,
    val isNewPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isUpdatingPassword: Boolean = false,
    val passwordErrorMessage: String? = null,
    val passwordSuccessMessage: String? = null,
    val errorMessage: String? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM
)

@OptIn(ExperimentalCoroutinesApi::class)
class MyProfileViewModel(
    private val authRepository: AuthRepository,
    private val profileReviewRepository: ProfileReviewRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val discoveryMatchRepository: DiscoveryMatchRepository,
    private val themeModeFlow: Flow<AppThemeMode> = flowOf(AppThemeMode.SYSTEM),
    private val onSetThemeMode: suspend (AppThemeMode) -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyProfileUiState())
    val uiState: StateFlow<MyProfileUiState> = _uiState.asStateFlow()
    private var isLoggingOut = false
    private var hasLoggedOut = false
    private var profileJob: Job? = null
    private var reviewsJob: Job? = null
    private var wardrobeJob: Job? = null
    private var unreadJob: Job? = null

    init {
        startObserving()
        viewModelScope.launch {
            themeModeFlow.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(themeMode = mode) }
            onSetThemeMode(mode)
        }
    }

    private fun startObserving() {
        profileJob?.cancel()
        reviewsJob?.cancel()
        wardrobeJob?.cancel()
        unreadJob?.cancel()

        profileJob = viewModelScope.launch {
            authRepository.observeCurrentUser().collect { user ->
                if ((isLoggingOut || hasLoggedOut) && user == null) {
                    return@collect
                }
                _uiState.update { current ->
                    current.copy(
                        userProfile = user,
                        isLoading = false,
                        errorMessage = if (user == null) {
                            "Profil Anda tidak dapat dimuat. Periksa koneksi internet Anda lalu coba muat ulang."
                        } else {
                            null
                        }
                    )
                }
            }
        }

        reviewsJob = viewModelScope.launch {
            authRepository.observeLoggedInUserId().flatMapLatest { userId ->
                if (userId == null) flowOf(emptyList())
                else profileReviewRepository.observeUserReviews(userId)
            }.collect { reviewsList ->
                _uiState.update { it.copy(reviews = reviewsList) }
            }
        }

        wardrobeJob = viewModelScope.launch {
            authRepository.observeLoggedInUserId().flatMapLatest { userId ->
                if (userId == null) flowOf(emptyList())
                else wardrobeRepository.observeMyWardrobe(userId)
            }.collect { items ->
                _uiState.update { it.copy(wardrobePreview = items.take(4)) }
            }
        }

        unreadJob = viewModelScope.launch {
            authRepository.observeLoggedInUserId()
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    if (userId == null) flowOf(0)
                    else discoveryMatchRepository.observeUnreadNotificationCount(userId)
                }
                .collect { count ->
                    _uiState.update { it.copy(unreadNotificationCount = count) }
                }
        }
    }

    fun retryLoadProfile() {
        hasLoggedOut = false
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        startObserving()
    }

    fun clearPasswordSuccessMessage() {
        _uiState.update { it.copy(passwordSuccessMessage = null) }
    }

    fun openChangePasswordDialog() {
        _uiState.update {
            it.copy(
                showChangePasswordDialog = true,
                currentPassword = "",
                newPassword = "",
                confirmPassword = "",
                isCurrentPasswordVisible = false,
                isNewPasswordVisible = false,
                isConfirmPasswordVisible = false,
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
                currentPassword = "",
                newPassword = "",
                confirmPassword = "",
                isCurrentPasswordVisible = false,
                isNewPasswordVisible = false,
                isConfirmPasswordVisible = false,
                passwordErrorMessage = null
            )
        }
    }

    fun onCurrentPasswordChanged(value: String) {
        _uiState.update { it.copy(currentPassword = value, passwordErrorMessage = null) }
    }

    fun onNewPasswordChanged(value: String) {
        _uiState.update { it.copy(newPassword = value, passwordErrorMessage = null) }
    }

    fun onConfirmPasswordChanged(value: String) {
        _uiState.update { it.copy(confirmPassword = value, passwordErrorMessage = null) }
    }

    fun toggleCurrentPasswordVisibility() {
        _uiState.update { it.copy(isCurrentPasswordVisible = !it.isCurrentPasswordVisible) }
    }

    fun toggleNewPasswordVisibility() {
        _uiState.update { it.copy(isNewPasswordVisible = !it.isNewPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    fun submitChangePassword() {
        val state = _uiState.value
        if (state.isUpdatingPassword) return
        if (state.currentPassword.isEmpty()) {
            _uiState.update { it.copy(passwordErrorMessage = "Password saat ini wajib diisi") }
            return
        }
        PasswordPolicy.validateNewPassword(state.newPassword)?.let { msg ->
            _uiState.update { it.copy(passwordErrorMessage = msg) }
            return
        }
        if (state.currentPassword == state.newPassword) {
            _uiState.update { it.copy(passwordErrorMessage = "Password baru tidak boleh sama dengan password saat ini") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(passwordErrorMessage = "Konfirmasi password tidak cocok") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingPassword = true, passwordErrorMessage = null) }
            when (val res = authRepository.changePassword(state.currentPassword, state.newPassword)) {
                is ChangePasswordResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingPassword = false,
                            showChangePasswordDialog = false,
                            currentPassword = "",
                            newPassword = "",
                            confirmPassword = "",
                            isCurrentPasswordVisible = false,
                            isNewPasswordVisible = false,
                            isConfirmPasswordVisible = false,
                            passwordSuccessMessage = "Password akun berhasil diubah. Sesi perangkat ini tetap aktif dan sesi lain telah dicabut."
                        )
                    }
                }
                is ChangePasswordResult.InvalidCurrentPassword -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingPassword = false,
                            passwordErrorMessage = "Password saat ini salah."
                        )
                    }
                }
                is ChangePasswordResult.Unauthorized -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingPassword = false,
                            passwordErrorMessage = "Sesi Anda tidak valid atau sudah berakhir. Silakan login kembali."
                        )
                    }
                }
                is ChangePasswordResult.ValidationFailed -> {
                    _uiState.update { it.copy(isUpdatingPassword = false, passwordErrorMessage = res.message) }
                }
                is ChangePasswordResult.DatabaseError -> {
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
        if (isLoggingOut) return
        isLoggingOut = true
        viewModelScope.launch {
            try {
                authRepository.logout()
                hasLoggedOut = true
                onSuccess()
            } finally {
                isLoggingOut = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication)
                MyProfileViewModel(
                    authRepository = app.container.authRepository,
                    profileReviewRepository = app.container.profileReviewRepository,
                    wardrobeRepository = app.container.wardrobeRepository,
                    discoveryMatchRepository = app.container.discoveryMatchRepository,
                    themeModeFlow = app.container.sessionPrefs.themeModeFlow,
                    onSetThemeMode = app.container.sessionPrefs::setThemeMode
                )
            }
        }
    }
}
