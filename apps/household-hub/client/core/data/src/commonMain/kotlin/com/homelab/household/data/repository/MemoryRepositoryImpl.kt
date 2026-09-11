package com.homelab.household.data.repository

import com.homelab.household.data.dto.MemoryReadDto
import com.homelab.household.data.dto.MemoryUpdateDto
import com.homelab.household.data.mapper.MemoryDataMapper
import com.homelab.household.data.remote.NetworkExceptionHelper
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.AgentMemory
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.repository.MemoryRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class MemoryRepositoryImpl(
    private val client: HttpClient,
    private val baseUrl: String
) : MemoryRepository {

    override suspend fun auditMemories(scope: MemoryScope?): List<AgentMemory> {
        return try {
            val dtoList = client.get("$baseUrl/api/v1/memories") {
                if (scope != null) {
                    parameter("scope", scope.name.lowercase())
                }
            }.body<List<MemoryReadDto>>()
            dtoList.map { MemoryDataMapper.toDomain(it) }
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    override suspend fun deleteMemory(memoryId: String) {
        try {
            client.delete("$baseUrl/api/v1/memories/$memoryId")
        } catch (e: Exception) {
            handleOfflineOrThrow(e)
        }
    }

    override suspend fun updateMemory(
        memoryId: String,
        content: String?,
        confidence: Float?,
        isActive: Boolean?
    ): AgentMemory {
        return try {
            val dto = client.patch("$baseUrl/api/v1/memories/$memoryId") {
                contentType(ContentType.Application.Json)
                setBody(
                    MemoryUpdateDto(
                        content = content,
                        confidence = confidence,
                        is_active = isActive
                    )
                )
            }.body<MemoryReadDto>()
            MemoryDataMapper.toDomain(dto)
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
