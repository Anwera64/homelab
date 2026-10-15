package com.homelab.household.presentation.dashboard

import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.domain.model.ServerStatus
import com.homelab.household.domain.model.User

data class DashboardUiState(
    val serverStatus: ServerStatus = ServerStatus.Connecting,
    val currentUser: User? = null,
    val recentSessions: List<ConversationSession> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
