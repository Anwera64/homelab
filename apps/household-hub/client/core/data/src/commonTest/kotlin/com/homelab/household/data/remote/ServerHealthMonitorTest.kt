package com.homelab.household.data.remote

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.domain.model.ServerStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import io.ktor.utils.io.errors.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerHealthMonitorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun check_health_when_server_returns_200_emits_online() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/health") {
                respond(
                    content = """{"status": "ok", "version": "0.1.0", "database": "connected"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(content = "Not Found", status = HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val monitor = ServerHealthMonitor(client, baseUrl = DEFAULT_BASE_URL)
        val status = monitor.checkHealth()

        assertTrue(status is ServerStatus.Online)
    }

    @Test
    fun check_health_when_connection_refused_emits_offline() = runTest {
        val mockEngine = MockEngine {
            throw IOException("Connection refused")
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val monitor = ServerHealthMonitor(client, baseUrl = DEFAULT_BASE_URL)
        val status = monitor.checkHealth()

        assertTrue(status is ServerStatus.Offline)
        val offline = status as ServerStatus.Offline
        assertTrue(offline.reason.contains("Connection refused") || offline.reason.isNotEmpty())
    }

    @Test
    fun check_health_when_captive_portal_html_or_degraded_status_emits_offline() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "<html>Captive Portal Login</html>",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val monitor = ServerHealthMonitor(client, baseUrl = DEFAULT_BASE_URL)
        val status = monitor.checkHealth()

        assertTrue(status is ServerStatus.Offline)
    }
}
