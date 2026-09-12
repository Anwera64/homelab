package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.repository.AgentRepository

class CreateAgentUseCase(private val agentRepository: AgentRepository) {
    suspend operator fun invoke(agent: AgentPersonality): AgentPersonality {
        if (agent.slug.isBlank()) throw ValidationException("Agent slug cannot be blank")
        if (agent.name.isBlank()) throw ValidationException("Agent name cannot be blank")
        if (agent.systemPrompt.isBlank()) throw ValidationException("System prompt cannot be blank")
        return agentRepository.createAgent(agent)
    }
}
