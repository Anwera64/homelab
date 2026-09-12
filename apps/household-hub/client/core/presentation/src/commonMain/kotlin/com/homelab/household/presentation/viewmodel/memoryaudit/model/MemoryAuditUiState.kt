package com.homelab.household.presentation.viewmodel.memoryaudit.model

import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.HouseholdMilestone
import com.homelab.household.domain.model.MemoryScope

data class MemoryAuditUiState(
    val isLoading: Boolean = false,
    val memories: List<AgentMemory> = emptyList(),
    val userMilestones: List<HouseholdMilestone> = emptyList(),
    val selectedScope: MemoryScope? = null,
    val errorMessage: String? = null
)
