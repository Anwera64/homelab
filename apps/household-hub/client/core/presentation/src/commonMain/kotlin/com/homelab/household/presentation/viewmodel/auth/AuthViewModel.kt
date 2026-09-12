package com.homelab.household.presentation.viewmodel.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.usecase.CheckAuthStatusUseCase
import com.homelab.household.domain.usecase.FirstRunOnboardUseCase
import com.homelab.household.domain.usecase.GetHubHostUseCase
import com.homelab.household.domain.usecase.LoginUseCase
import com.homelab.household.presentation.viewmodel.auth.model.AuthUiState
import com.homelab.household.presentation.viewmodel.auth.model.HubStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val onboardUseCase: FirstRunOnboardUseCase,
    private val checkAuthStatusUseCase: CheckAuthStatusUseCase,
    private val getHubHostUseCase: GetHubHostUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(hubAddress = getHubHostUseCase()))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun checkStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, hubStatus = HubStatus.Checking) }
            try {
                val status = checkAuthStatusUseCase()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isInitialized = status.isInitialized,
                        memberCount = status.memberCount,
                        hubStatus = if (status.isInitialized) {
                            HubStatus.Ready(memberCount = status.memberCount)
                        } else {
                            HubStatus.FirstRun
                        }
                    )
                }
            } catch (e: ServerOfflineException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message,
                        hubStatus = HubStatus.Unreachable
                    )
                }
            } catch (e: Throwable) {
                val message = e.message ?: "Failed to check status"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = message,
                        hubStatus = HubStatus.Failed(message = message)
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
