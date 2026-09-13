package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.AgentPersonality

fun interface GetAgentUseCase {
    suspend operator fun invoke(agentId: String): AgentPersonality
}
