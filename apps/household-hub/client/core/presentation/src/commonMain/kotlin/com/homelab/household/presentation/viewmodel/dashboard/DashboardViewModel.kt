package com.homelab.household.presentation.viewmodel.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.ListHouseholdMilestonesUseCase
import com.homelab.household.domain.usecase.ListSessionsUseCase
import com.homelab.household.domain.usecase.LogoutUseCase
import com.homelab.household.domain.usecase.ObserveServerStatusUseCase
import com.homelab.household.presentation.viewmodel.dashboard.model.DashboardUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val observeServerStatusUseCase: ObserveServerStatusUseCase,
    private val listRecentSessionsUseCase: ListSessionsUseCase,
    private val listHouseholdMilestonesUseCase: ListHouseholdMilestonesUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeServerStatus()
    }

    private fun observeServerStatus() {
        viewModelScope.launch {
            observeServerStatusUseCase().collect { status ->
                _uiState.update { it.copy(serverStatus = status) }
            }
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val user = getCurrentUserUseCase()
                val sessions = listRecentSessionsUseCase()
                val milestones = listHouseholdMilestonesUseCase()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentUser = user,
                        recentSessions = sessions,
                        householdMilestones = milestones
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load dashboard"
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                logoutUseCase()
                _uiState.update { it.copy(currentUser = null) }
            } catch (e: Throwable) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to logout") }
            }
        }
    }
}
