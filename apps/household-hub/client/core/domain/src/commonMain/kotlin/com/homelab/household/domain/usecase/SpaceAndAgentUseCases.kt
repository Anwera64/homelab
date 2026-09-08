package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.model.Space
import com.homelab.household.domain.repository.AgentRepository
import com.homelab.household.domain.repository.SpaceRepository

class GetPersonalSpaceUseCase(private val spaceRepository: SpaceRepository) {
    suspend operator fun invoke(): Space = spaceRepository.getPersonalSpace()
}

class GetHouseholdSpaceUseCase(private val spaceRepository: SpaceRepository) {
    suspend operator fun invoke(): Space = spaceRepository.getHouseholdSpace()
}

class UpdateSpaceSettingsUseCase(private val spaceRepository: SpaceRepository) {
    suspend operator fun invoke(spaceId: String, settings: Map<String, Any?>): Space {
        if (spaceId.isBlank()) throw ValidationException("Space ID cannot be blank")
        return spaceRepository.updateSpaceSettings(spaceId, settings)
    }
}

class ListAgentsUseCase(private val agentRepository: AgentRepository) {
    suspend operator fun invoke(): List<AgentPersonality> = agentRepository.listAgents()
}

class GetAgentUseCase(private val agentRepository: AgentRepository) {
    suspend operator fun invoke(agentId: String): AgentPersonality {
        if (agentId.isBlank()) throw ValidationException("Agent ID cannot be blank")
        return agentRepository.getAgent(agentId)
    }
}

class CreateAgentUseCase(private val agentRepository: AgentRepository) {
    suspend operator fun invoke(agent: AgentPersonality): AgentPersonality {
        if (agent.slug.isBlank()) throw ValidationException("Agent slug cannot be blank")
        if (agent.name.isBlank()) throw ValidationException("Agent name cannot be blank")
        if (agent.systemPrompt.isBlank()) throw ValidationException("System prompt cannot be blank")
        return agentRepository.createAgent(agent)
    }
}

class UpdateAgentUseCase(private val agentRepository: AgentRepository) {
    suspend operator fun invoke(agent: AgentPersonality): AgentPersonality {
        if (agent.id.isBlank()) throw ValidationException("Agent ID cannot be blank")
        return agentRepository.updateAgent(agent)
    }
}

class DeleteAgentUseCase(private val agentRepository: AgentRepository) {
    suspend operator fun invoke(agentId: String) {
        if (agentId.isBlank()) throw ValidationException("Agent ID cannot be blank")
        agentRepository.deleteAgent(agentId)
    }
}

class RestoreAgentUseCase(private val agentRepository: AgentRepository) {
    suspend operator fun invoke(agentId: String): AgentPersonality {
        if (agentId.isBlank()) throw ValidationException("Agent ID cannot be blank")
        return agentRepository.restoreAgent(agentId)
    }
}
