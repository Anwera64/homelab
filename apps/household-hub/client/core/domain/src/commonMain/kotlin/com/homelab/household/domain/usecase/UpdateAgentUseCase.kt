package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.repository.AgentRepository

class UpdateAgentUseCase(private val agentRepository: AgentRepository) {
    suspend operator fun invoke(agent: AgentPersonality): AgentPersonality {
        if (agent.id.isBlank()) throw ValidationException("Agent ID cannot be blank")
        return agentRepository.updateAgent(agent)
    }
}
