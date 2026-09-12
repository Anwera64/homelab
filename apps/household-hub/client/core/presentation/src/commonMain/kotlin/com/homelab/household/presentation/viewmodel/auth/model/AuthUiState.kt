package com.homelab.household.presentation.viewmodel.auth.model

import com.homelab.household.domain.model.User

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isInitialized: Boolean = true,
    val memberCount: Int = 0,
    val user: User? = null,
    val errorMessage: String? = null,
    val hubStatus: HubStatus = HubStatus.Checking,
    val hubAddress: String = ""
)
