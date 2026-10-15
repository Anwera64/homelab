package com.homelab.household.data.datasource.remote

import com.homelab.household.data.datasource.remote.`interface`.MemoryRemoteDataSource
import com.homelab.household.data.dto.MemoryPageDto
import com.homelab.household.data.network.ensureJsonSuccess
import com.homelab.household.data.network.reachingHub
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post

class KtorMemoryRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) : MemoryRemoteDataSource {
    override suspend fun getMemoryPage(): MemoryPageDto =
        reachingHub {
            client.get("$baseUrl/api/v1/memories/page").ensureJsonSuccess().body()
        }

    override suspend fun removeMemory(memoryId: String): Unit =
        reachingHub {
            client.delete("$baseUrl/api/v1/memories/$memoryId").ensureJsonSuccess()
        }

    override suspend fun restoreMemory(memoryId: String): Unit =
        reachingHub {
            client.post("$baseUrl/api/v1/memories/$memoryId/restore").ensureJsonSuccess()
        }
}
