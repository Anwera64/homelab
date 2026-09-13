package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.AgentPersonality

fun interface CreateAgentUseCase {
    suspend operator fun invoke(agent: AgentPersonality): AgentPersonality
}
