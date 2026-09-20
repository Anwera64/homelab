package com.homelab.household.data.datasource.remote

import com.homelab.household.data.dto.HealthCheckDto
import com.homelab.household.domain.model.ServerStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class KtorServerStatusRemoteDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) : ServerStatusRemoteDataSource {
    override suspend fun checkHealth(): ServerStatus {
        val mark = TimeSource.Monotonic.markNow()
        return try {
            val response = client.get("$baseUrl/api/v1/health")
            if (response.status.isSuccess()) {
                val dto = response.body<HealthCheckDto>()
                if (dto.status.equals("ok", ignoreCase = true)) {
                    val latency = mark.elapsedNow().inWholeMilliseconds
                    ServerStatus.Online(latencyMs = latency)
                } else {
                    ServerStatus.Offline(reason = "Degraded status: ${dto.status}")
                }
            } else {
                ServerStatus.Offline(reason = "HTTP ${response.status.value}")
            }
        } catch (e: Throwable) {
            ServerStatus.Offline(reason = e.message ?: "Connection failed")
        }
    }

    override fun observeStatus(intervalSeconds: Long): Flow<ServerStatus> = flow {
        var currentDelay = intervalSeconds.seconds
        while (currentCoroutineContext().isActive) {
            val status = checkHealth()
            emit(status)
            when (status) {
                is ServerStatus.Online -> currentDelay = intervalSeconds.seconds
                is ServerStatus.Offline -> {
                    // Exponential backoff up to 60s
                    currentDelay = (currentDelay * 1.5).coerceAtMost(60.seconds)
                }
                else -> {}
            }
            delay(currentDelay)
        }
    }
}
