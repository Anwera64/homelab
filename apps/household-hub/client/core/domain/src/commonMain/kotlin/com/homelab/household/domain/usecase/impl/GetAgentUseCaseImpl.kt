package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.repository.AgentRepository
import com.homelab.household.domain.usecase.GetAgentUseCase

class GetAgentUseCaseImpl(
    private val agentRepository: AgentRepository,
) : GetAgentUseCase {
    override suspend operator fun invoke(agentId: String): AgentPersonality {
        if (agentId.isBlank()) throw ValidationException("Agent ID cannot be blank")
        return agentRepository.getAgent(agentId)
    }
}
