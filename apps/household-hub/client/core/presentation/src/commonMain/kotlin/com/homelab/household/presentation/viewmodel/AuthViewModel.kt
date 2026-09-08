package com.homelab.household.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.CheckAuthStatusUseCase
import com.homelab.household.domain.usecase.FirstRunOnboardUseCase
import com.homelab.household.domain.usecase.LoginUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isInitialized: Boolean = true,
    val memberCount: Int = 0,
    val user: User? = null,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val onboardUseCase: FirstRunOnboardUseCase,
    private val checkAuthStatusUseCase: CheckAuthStatusUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun checkStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val status = checkAuthStatusUseCase()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isInitialized = status.isInitialized,
                        memberCount = status.memberCount
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to check status"
                    )
                }
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val user = loginUseCase(username, password)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        user = user,
                        errorMessage = null
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = false,
                        user = null,
                        errorMessage = e.message ?: "Authentication failed"
                    )
                }
            }
        }
    }

    fun onboard(
        username: String,
        email: String,
        password: String,
        fullName: String,
        avatarColor: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val user = onboardUseCase(username, email, password, fullName, avatarColor)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        user = user,
                        errorMessage = null
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = false,
                        user = null,
                        errorMessage = e.message ?: "Onboarding failed"
                    )
                }
            }
        }
    }
}
