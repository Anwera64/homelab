package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.AgentRepository

class DeleteAgentUseCase(private val agentRepository: AgentRepository) {
    suspend operator fun invoke(agentId: String) {
        if (agentId.isBlank()) throw ValidationException("Agent ID cannot be blank")
        agentRepository.deleteAgent(agentId)
    }
}
