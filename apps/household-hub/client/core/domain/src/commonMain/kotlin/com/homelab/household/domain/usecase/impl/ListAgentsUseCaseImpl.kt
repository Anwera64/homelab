package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.repository.AgentRepository
import com.homelab.household.domain.usecase.ListAgentsUseCase

class ListAgentsUseCaseImpl(
    private val agentRepository: AgentRepository,
) : ListAgentsUseCase {
    override suspend operator fun invoke(): List<AgentPersonality> = agentRepository.listAgents()
}
