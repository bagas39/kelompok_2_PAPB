package com.example.cloble.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.CityPreset
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.result.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val city: String = CityPreset.BANDUNG.displayName,
    val password: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

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

    fun onCityChanged(newCity: String) {
        _uiState.update { it.copy(city = newCity, errorMessage = null) }
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.update { it.copy(password = newPassword, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(newConfirm: String) {
        _uiState.update { it.copy(confirmPassword = newConfirm, errorMessage = null) }
    }

    fun submitRegister(onSuccess: () -> Unit) {
        val s = _uiState.value
        val name = s.name.trim()
        val email = s.email.trim()

        if (name.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Nama lengkap wajib diisi") }
            return
        }
        if (email.isEmpty() || !email.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Format email tidak valid") }
            return
        }
        if (s.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password minimal 6 karakter") }
            return
        }
        if (s.password != s.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Konfirmasi password tidak cocok") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = authRepository.register(name, email, s.password, s.city)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    onSuccess()
                }
                is AuthResult.EmailAlreadyRegistered -> {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = "Email sudah terdaftar. Silakan gunakan email lain.") }
                }
                is AuthResult.ValidationFailed -> {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                }
                else -> {
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
