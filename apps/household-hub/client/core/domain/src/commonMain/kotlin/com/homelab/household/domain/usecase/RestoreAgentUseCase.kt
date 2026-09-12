package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.repository.AgentRepository

class RestoreAgentUseCase(private val agentRepository: AgentRepository) {
    suspend operator fun invoke(agentId: String): AgentPersonality {
        if (agentId.isBlank()) throw ValidationException("Agent ID cannot be blank")
        return agentRepository.restoreAgent(agentId)
    }
}
