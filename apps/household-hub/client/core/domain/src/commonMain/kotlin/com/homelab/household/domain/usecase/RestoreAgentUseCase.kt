package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.AgentPersonality

fun interface RestoreAgentUseCase {
    suspend operator fun invoke(agentId: String): AgentPersonality
}
