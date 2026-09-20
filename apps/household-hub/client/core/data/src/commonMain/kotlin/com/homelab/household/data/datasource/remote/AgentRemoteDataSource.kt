package com.homelab.household.data.datasource.remote

import com.homelab.household.data.dto.AgentCreateDto
import com.homelab.household.data.dto.AgentReadDto
import com.homelab.household.data.dto.AgentUpdateDto

/**
 * Everything the hub is asked about the household's agents: who they are, and what an admin can
 * change about one's personality. Each function is one call, returning the DTO the hub sent.
 * `AgentRepositoryImpl` maps those DTOs to and from `AgentPersonality`.
 */
interface AgentRemoteDataSource {

    suspend fun listAgents(): List<AgentReadDto>

    suspend fun getAgent(agentId: String): AgentReadDto

    suspend fun createAgent(agent: AgentCreateDto): AgentReadDto

    /** [agentId] is passed separately because the DTO the hub expects for an update carries no id. */
    suspend fun updateAgent(agentId: String, agent: AgentUpdateDto): AgentReadDto

    suspend fun deleteAgent(agentId: String)

    suspend fun restoreAgent(agentId: String): AgentReadDto
}
