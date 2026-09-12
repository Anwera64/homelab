package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.repository.AgentRepository

class ListAgentsUseCase(private val agentRepository: AgentRepository) {
    suspend operator fun invoke(): List<AgentPersonality> = agentRepository.listAgents()
}
