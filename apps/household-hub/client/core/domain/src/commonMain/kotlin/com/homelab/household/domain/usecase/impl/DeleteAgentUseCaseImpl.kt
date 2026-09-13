package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.AgentRepository
import com.homelab.household.domain.usecase.DeleteAgentUseCase

class DeleteAgentUseCaseImpl(private val agentRepository: AgentRepository) : DeleteAgentUseCase {
    override suspend operator fun invoke(agentId: String) {
        if (agentId.isBlank()) throw ValidationException("Agent ID cannot be blank")
        agentRepository.deleteAgent(agentId)
    }
}
