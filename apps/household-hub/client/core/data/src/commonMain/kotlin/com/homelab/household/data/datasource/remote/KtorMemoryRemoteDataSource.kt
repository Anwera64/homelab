package com.homelab.household.data.datasource.remote

import com.homelab.household.data.datasource.remote.`interface`.MemoryRemoteDataSource
import com.homelab.household.data.dto.MemoryReadDto
import com.homelab.household.data.dto.MemoryUpdateDto
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.reachingHub
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorMemoryRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) : MemoryRemoteDataSource {
    override suspend fun auditMemories(scope: String?): List<MemoryReadDto> =
        reachingHub {
            client
                .get("$baseUrl/api/v1/memories") {
                    if (scope != null) {
                        parameter("scope", scope)
                    }
                }.ensureJsonSuccess()
                .body()
        }

    override suspend fun deleteMemory(memoryId: String): Unit =
        reachingHub {
            client.delete("$baseUrl/api/v1/memories/$memoryId").ensureJsonSuccess()
        }

    override suspend fun updateMemory(
        memoryId: String,
        memory: MemoryUpdateDto,
    ): MemoryReadDto =
        reachingHub {
            client
                .patch("$baseUrl/api/v1/memories/$memoryId") {
                    contentType(ContentType.Application.Json)
                    setBody(memory)
                }.ensureJsonSuccess()
                .body()
        }
}
