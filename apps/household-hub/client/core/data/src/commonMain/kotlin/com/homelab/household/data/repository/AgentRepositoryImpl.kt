package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.AgentRemoteDataSource
import com.homelab.household.data.mapper.AgentDataMapper
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.repository.AgentRepository

/**
 * Orchestration and mapping for the household's agents. [remote] speaks DTOs; every DTO becomes an
 * `AgentPersonality` here, and creating or updating one builds the DTO the hub expects.
 */
class AgentRepositoryImpl(
    private val remote: AgentRemoteDataSource,
) : AgentRepository {

    override suspend fun listAgents(): List<AgentPersonality> =
        remote.listAgents().map(AgentDataMapper::toDomain)

    override suspend fun getAgent(agentId: String): AgentPersonality =
        AgentDataMapper.toDomain(remote.getAgent(agentId))

    override suspend fun createAgent(agent: AgentPersonality): AgentPersonality =
        AgentDataMapper.toDomain(remote.createAgent(AgentDataMapper.toCreateDto(agent)))

    override suspend fun updateAgent(agent: AgentPersonality): AgentPersonality =
        AgentDataMapper.toDomain(remote.updateAgent(agent.id, AgentDataMapper.toUpdateDto(agent)))

    override suspend fun deleteAgent(agentId: String) = remote.deleteAgent(agentId)

    override suspend fun restoreAgent(agentId: String): AgentPersonality =
        AgentDataMapper.toDomain(remote.restoreAgent(agentId))
}
