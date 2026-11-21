package com.example.cloble.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.result.RequestPasswordResetOtpResult
import com.example.cloble.domain.result.ResetPasswordResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val resetSuccessMessage: String? = null,
    val showResetDialog: Boolean = false,
    val resetStep: Int = 1,
    val resetEmail: String = "",
    val resetOtpCode: String = "",
    val resetNewPassword: String = "",
    val resetConfirmPassword: String = "",
    val isResetSubmitting: Boolean = false,
    val resetErrorMessage: String? = null,
    val resetInfoMessage: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChanged(newEmail: String) {
        _uiState.update { it.copy(email = newEmail, errorMessage = null, resetSuccessMessage = null) }
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.update { it.copy(password = newPassword, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun openResetPasswordDialog() {
        _uiState.update { current ->
            current.copy(
                showResetDialog = true,
                resetStep = 1,
                resetEmail = current.email.trim(),
                resetOtpCode = "",
                resetNewPassword = "",
                resetConfirmPassword = "",
                resetErrorMessage = null,
                resetInfoMessage = null
            )
        }
    }

    fun dismissResetPasswordDialog() {
        _uiState.update {
            it.copy(
                showResetDialog = false,
                resetStep = 1,
                isResetSubmitting = false,
                resetErrorMessage = null,
                resetInfoMessage = null
            )
        }
    }

    fun onResetEmailChanged(newEmail: String) {
        _uiState.update { it.copy(resetEmail = newEmail, resetErrorMessage = null) }
    }

    fun onResetOtpCodeChanged(newOtp: String) {
        val digitsOnly = newOtp.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(resetOtpCode = digitsOnly, resetErrorMessage = null) }
    }

    fun backToResetEmailStep() {
        _uiState.update { it.copy(resetStep = 1, resetErrorMessage = null, resetInfoMessage = null) }
    }

    fun onResetNewPasswordChanged(newPassword: String) {
        _uiState.update { it.copy(resetNewPassword = newPassword, resetErrorMessage = null) }
    }

    fun onResetConfirmPasswordChanged(newConfirm: String) {
        _uiState.update { it.copy(resetConfirmPassword = newConfirm, resetErrorMessage = null) }
    }

    fun requestResetOtp() {
        val email = _uiState.value.resetEmail.trim()
        if (email.isEmpty() || !email.contains("@")) {
            _uiState.update { it.copy(resetErrorMessage = "Format email tidak valid") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isResetSubmitting = true, resetErrorMessage = null, resetInfoMessage = null) }
            when (val result = authRepository.requestPasswordResetOtp(email)) {
                is RequestPasswordResetOtpResult.Success,
                is RequestPasswordResetOtpResult.EmailNotFound -> {
                    _uiState.update {
                        it.copy(
                            isResetSubmitting = false,
                            resetStep = 2,
                            resetOtpCode = "",
                            resetErrorMessage = null,
                            resetInfoMessage = "Kami akan mengirimkan OTP jika email Anda terdaftar. Kode berlaku 10 menit (maksimal 5 kali percobaan)."
                        )
                    }
                }
                is RequestPasswordResetOtpResult.RateLimited -> {
                    _uiState.update {
                        it.copy(
                            isResetSubmitting = false,
                            resetErrorMessage = result.message
                        )
                    }
                }
                is RequestPasswordResetOtpResult.SmtpNotConfigured -> {
                    _uiState.update {
                        it.copy(
                            isResetSubmitting = false,
                            resetErrorMessage = result.message
                        )
                    }
                }
                is RequestPasswordResetOtpResult.ValidationFailed -> {
                    _uiState.update {
                        it.copy(
                            isResetSubmitting = false,
                            resetErrorMessage = result.message
                        )
                    }
                }
                is RequestPasswordResetOtpResult.DatabaseError -> {
                    _uiState.update {
                        it.copy(
                            isResetSubmitting = false,
                            resetErrorMessage = result.failure.message ?: "Gagal mengirim kode OTP."
                        )
                    }
                }
            }
        }
    }

    fun submitResetPassword() {
        val state = _uiState.value
        val email = state.resetEmail.trim()
        val otp = state.resetOtpCode.trim()
        val newPassword = state.resetNewPassword
        val confirm = state.resetConfirmPassword

        if (email.isEmpty() || !email.contains("@")) {
            _uiState.update { it.copy(resetErrorMessage = "Format email tidak valid") }
            return
        }
        if (otp.length != 6) {
            _uiState.update { it.copy(resetErrorMessage = "Kode OTP harus terdiri dari 6 digit angka") }
            return
        }
        if (newPassword.length < 6) {
            _uiState.update { it.copy(resetErrorMessage = "Password baru minimal 6 karakter") }
            return
        }
        if (newPassword != confirm) {
            _uiState.update { it.copy(resetErrorMessage = "Konfirmasi password tidak cocok") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isResetSubmitting = true, resetErrorMessage = null) }
            when (val result = authRepository.verifyOtpAndResetPassword(email, otp, newPassword)) {
                is ResetPasswordResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isResetSubmitting = false,
                            showResetDialog = false,
                            resetStep = 1,
                            resetOtpCode = "",
                            email = email,
                            password = "",
                            errorMessage = null,
                            resetInfoMessage = null,
                            resetSuccessMessage = "Password untuk $email berhasil diperbarui. Silakan masuk dengan password baru."
                        )
                    }
                }
                is ResetPasswordResult.InvalidOtp -> {
                    _uiState.update {
                        it.copy(
                            isResetSubmitting = false,
                            resetErrorMessage = "Kode OTP salah. Sisa percobaan: ${result.remainingAttempts} kali."
                        )
                    }
                }
                is ResetPasswordResult.OtpExpiredOrNotFound,
                is ResetPasswordResult.EmailNotFound -> {
                    _uiState.update {
                        it.copy(
                            isResetSubmitting = false,
                            resetErrorMessage = "Kode OTP tidak ditemukan atau sudah kedaluwarsa. Minta kode OTP baru."
                        )
                    }
                }
                is ResetPasswordResult.OtpMaxAttemptsExceeded -> {
                    _uiState.update {
                        it.copy(
                            isResetSubmitting = false,
                            resetStep = 1,
                            resetOtpCode = "",
                            resetInfoMessage = null,
                            resetErrorMessage = "Batas 5 kali salah kode OTP terlampaui. Silakan kirim ulang kode OTP."
                        )
                    }
                }
                is ResetPasswordResult.ValidationFailed -> {
                    _uiState.update {
                        it.copy(
                            isResetSubmitting = false,
                            resetErrorMessage = result.message
                        )
                    }
                }
                is ResetPasswordResult.DatabaseError -> {
                    _uiState.update {
                        it.copy(
                            isResetSubmitting = false,
                            resetErrorMessage = result.failure.message ?: "Gagal mereset password."
                        )
                    }
                }
            }
        }
    }

    fun submitLogin(onSuccess: () -> Unit) {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        if (email.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Email dan password wajib diisi") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, resetSuccessMessage = null) }
            when (val result = authRepository.login(email, password)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                is AuthResult.InvalidCredentials -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Email atau password salah") }
                }
                is AuthResult.EmailAlreadyRegistered -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Email sudah terdaftar") }
                }
                is AuthResult.ValidationFailed -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is AuthResult.DatabaseError -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.failure.message ?: "Terjadi kesalahan database") }
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication)
                LoginViewModel(app.container.authRepository)
            }
        }
    }
}
