package com.example.cloble.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.util.PasswordPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    val hasSelectedLocation: Boolean
        get() = city.isNotBlank() && latitude != null && longitude != null
}

class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNameChanged(newName: String) {
        _uiState.update { it.copy(name = newName, errorMessage = null) }
    }

    fun onEmailChanged(newEmail: String) {
        _uiState.update { it.copy(email = newEmail, errorMessage = null) }
    }

    fun onLocationSelected(city: String, latitude: Double, longitude: Double) {
        _uiState.update {
            it.copy(
                city = city.trim(),
                latitude = latitude,
                longitude = longitude,
                errorMessage = null
            )
        }
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.update { it.copy(password = newPassword, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(newConfirm: String) {
        _uiState.update { it.copy(confirmPassword = newConfirm, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    fun submitRegister(onSuccess: () -> Unit) {
        val s = _uiState.value
        if (s.isSubmitting) return
        val name = s.name.trim()
        val email = s.email.trim()
        val city = s.city.trim()

        if (name.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Nama lengkap wajib diisi") }
            return
        }
        if (!PasswordPolicy.isValidEmail(email)) {
            _uiState.update { it.copy(errorMessage = "Format email tidak valid") }
            return
        }
        if (!s.hasSelectedLocation) {
            _uiState.update { it.copy(errorMessage = "Pilih lokasi domisili Anda di peta terlebih dahulu") }
            return
        }
        PasswordPolicy.validateNewPassword(s.password)?.let { msg ->
            _uiState.update { it.copy(errorMessage = msg) }
            return
        }
        if (s.password != s.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Konfirmasi password tidak cocok") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (
                val result = authRepository.register(
                    name = name,
                    email = email,
                    rawPassword = s.password,
                    city = city,
                    latitude = s.latitude,
                    longitude = s.longitude
                )
            ) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    onSuccess()
                }
                is AuthResult.EmailAlreadyRegistered -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "Email sudah terdaftar. Silakan gunakan email lain."
                        )
                    }
                }
                is AuthResult.ValidationFailed -> {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                }
                is AuthResult.RateLimited -> {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                }
                is AuthResult.DatabaseError -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.failure.message ?: "Pendaftaran gagal. Coba lagi."
                        )
                    }
                }
                AuthResult.InvalidCredentials -> {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = "Pendaftaran gagal. Coba lagi.") }
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloBleApplication)
                RegisterViewModel(app.container.authRepository)
            }
        }
    }
}
