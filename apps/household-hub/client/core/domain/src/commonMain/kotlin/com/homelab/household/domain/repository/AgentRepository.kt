package com.homelab.household.domain.repository

import com.homelab.household.domain.model.AgentPersonality

interface AgentRepository {
    suspend fun listAgents(): List<AgentPersonality>
    suspend fun getAgent(agentId: String): AgentPersonality
    suspend fun createAgent(agent: AgentPersonality): AgentPersonality
    suspend fun updateAgent(agent: AgentPersonality): AgentPersonality
    suspend fun deleteAgent(agentId: String)
    suspend fun restoreAgent(agentId: String): AgentPersonality
}
