package com.homelab.household.data.repository

import com.homelab.household.data.dto.AgentCreateDto
import com.homelab.household.data.dto.AgentReadDto
import com.homelab.household.data.dto.AgentUpdateDto
import com.homelab.household.data.mapper.AgentDataMapper
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.AgentPersonality
import com.homelab.household.domain.repository.AgentRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import com.homelab.household.data.remote.NetworkExceptionHelper

class AgentRepositoryImpl(
    private val client: HttpClient,
    private val baseUrl: String = "https://hub.spicy-llama.duckdns.org"
) : AgentRepository {

    override suspend fun listAgents(): List<AgentPersonality> {
        return try {
            val dtoList = client.get("$baseUrl/api/v1/agents").body<List<AgentReadDto>>()
            dtoList.map { AgentDataMapper.toDomain(it) }
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    override suspend fun getAgent(agentId: String): AgentPersonality {
        return try {
            val dto = client.get("$baseUrl/api/v1/agents/$agentId").body<AgentReadDto>()
            AgentDataMapper.toDomain(dto)
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    override suspend fun createAgent(agent: AgentPersonality): AgentPersonality {
        return try {
            val dto = client.post("$baseUrl/api/v1/agents") {
                contentType(ContentType.Application.Json)
                setBody(
                    AgentCreateDto(
                        slug = agent.slug,
                        name = agent.name,
                        description = agent.description,
                        avatar = agent.avatar,
                        system_prompt = agent.systemPrompt,
                        model_alias = agent.modelAlias,
                        temperature = agent.temperature,
                        top_p = agent.topP,
                        tool_permissions = agent.toolPermissions
                    )
                )
            }.body<AgentReadDto>()
            AgentDataMapper.toDomain(dto)
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    override suspend fun updateAgent(agent: AgentPersonality): AgentPersonality {
        return try {
            val dto = client.put("$baseUrl/api/v1/agents/${agent.id}") {
                contentType(ContentType.Application.Json)
                setBody(
                    AgentUpdateDto(
                        name = agent.name,
                        description = agent.description,
                        avatar = agent.avatar,
                        system_prompt = agent.systemPrompt,
                        model_alias = agent.modelAlias,
                        temperature = agent.temperature,
                        top_p = agent.topP,
                        tool_permissions = agent.toolPermissions,
                        is_active = agent.isActive
                    )
                )
            }.body<AgentReadDto>()
            AgentDataMapper.toDomain(dto)
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    override suspend fun deleteAgent(agentId: String) {
        try {
            client.delete("$baseUrl/api/v1/agents/$agentId")
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    override suspend fun restoreAgent(agentId: String): AgentPersonality {
        return try {
            val dto = client.post("$baseUrl/api/v1/agents/$agentId/restore").body<AgentReadDto>()
            AgentDataMapper.toDomain(dto)
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    private fun <T> handleOfflineOrThrow(e: Throwable): T {
        if (NetworkExceptionHelper.isNetworkOfflineException(e)) {
            throw ServerOfflineException(message = e.message ?: "Server is offline", cause = e)
        }
        throw e
    }
}
