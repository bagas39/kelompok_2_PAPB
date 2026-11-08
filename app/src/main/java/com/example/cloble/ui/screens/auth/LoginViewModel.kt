package com.example.cloble.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.DemoAccountSummary
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.result.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val demoAccounts: List<DemoAccountSummary> = emptyList()
)

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getDemoAccounts().collect { demos ->
                _uiState.update { it.copy(demoAccounts = demos) }
            }
        }
    }

    fun onEmailChanged(newEmail: String) {
        _uiState.update { it.copy(email = newEmail, errorMessage = null) }
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.update { it.copy(password = newPassword, errorMessage = null) }
    }

    fun submitLogin(onSuccess: () -> Unit) {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        if (email.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Email dan password wajib diisi") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
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

    fun quickLoginDemo(userId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.loginDemoAccount(userId)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                else -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Gagal login akun demo") }
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
