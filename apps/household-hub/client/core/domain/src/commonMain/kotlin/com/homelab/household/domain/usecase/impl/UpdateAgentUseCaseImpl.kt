package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.repository.AgentRepository
import com.homelab.household.domain.usecase.UpdateAgentUseCase

class UpdateAgentUseCaseImpl(
    private val agentRepository: AgentRepository,
) : UpdateAgentUseCase {
    override suspend operator fun invoke(agent: AgentPersonality): AgentPersonality {
        if (agent.id.isBlank()) throw ValidationException("Agent ID cannot be blank")
        return agentRepository.updateAgent(agent)
    }
}
