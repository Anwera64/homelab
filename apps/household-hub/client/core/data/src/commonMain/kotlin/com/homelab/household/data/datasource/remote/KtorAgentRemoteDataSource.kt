package com.homelab.household.data.datasource.remote

import com.homelab.household.data.dto.AgentCreateDto
import com.homelab.household.data.dto.AgentReadDto
import com.homelab.household.data.dto.AgentUpdateDto
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.reachingHub
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorAgentRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) : AgentRemoteDataSource {

    override suspend fun listAgents(): List<AgentReadDto> = reachingHub {
        client.get("$baseUrl/api/v1/agents").ensureJsonSuccess().body()
    }

    override suspend fun getAgent(agentId: String): AgentReadDto = reachingHub {
        client.get("$baseUrl/api/v1/agents/$agentId").ensureJsonSuccess().body()
    }

    override suspend fun createAgent(agent: AgentCreateDto): AgentReadDto = reachingHub {
        client.post("$baseUrl/api/v1/agents") {
            contentType(ContentType.Application.Json)
            setBody(agent)
        }.ensureJsonSuccess().body()
    }

    override suspend fun updateAgent(agentId: String, agent: AgentUpdateDto): AgentReadDto = reachingHub {
        client.put("$baseUrl/api/v1/agents/$agentId") {
            contentType(ContentType.Application.Json)
            setBody(agent)
        }.ensureJsonSuccess().body()
    }

    override suspend fun deleteAgent(agentId: String): Unit = reachingHub {
        client.delete("$baseUrl/api/v1/agents/$agentId").ensureJsonSuccess()
    }

    override suspend fun restoreAgent(agentId: String): AgentReadDto = reachingHub {
        client.post("$baseUrl/api/v1/agents/$agentId/restore").ensureJsonSuccess().body()
    }
}
