package com.homelab.household.data.repository

import com.homelab.household.data.assertThrowsSuspend
import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.data.local.InMemoryTokenStorage
import com.homelab.household.domain.exception.ServerOfflineException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class AuthRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun login_stores_token_and_returns_user() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/auth/login") {
                respond(
                    content = """{
                        "access_token": "jwt-token-123",
                        "token_type": "bearer",
                        "user": {
                            "id": "u-1",
                            "username": "alice",
                            "email": "alice@homelab.local",
                            "full_name": "Alice Doe",
                            "is_admin": true,
                            "is_active": true,
                            "personal_space_id": "sp-1",
                            "created_at": "2026-09-08T00:00:00Z"
                        }
                    }""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val tokenStorage = InMemoryTokenStorage()
        val repo = AuthRepositoryImpl(client, tokenStorage, baseUrl = DEFAULT_BASE_URL)

        val user = repo.login("alice", "password123")

        assertEquals("u-1", user.id)
        assertEquals("alice", user.username)
        assertEquals("jwt-token-123", tokenStorage.getAccessToken())
    }

    @Test
    fun check_status_when_hub_unreachable_throws_server_offline() = runTest {
        val mockEngine = MockEngine { throw IOException("Connection refused") }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val repo = AuthRepositoryImpl(client, InMemoryTokenStorage(), baseUrl = DEFAULT_BASE_URL)

        assertThrowsSuspend<ServerOfflineException> { repo.checkStatus() }
    }

    @Test
    fun check_status_when_proxy_returns_502_bad_gateway_throws_server_offline() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Bad Gateway",
                status = HttpStatusCode.BadGateway,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val repo = AuthRepositoryImpl(client, InMemoryTokenStorage(), baseUrl = DEFAULT_BASE_URL)

        assertThrowsSuspend<ServerOfflineException> { repo.checkStatus() }
    }

    @Test
    fun check_status_when_proxy_returns_404_html_throws_domain_exception() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "<html><body>404 Not Found</body></html>",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val repo = AuthRepositoryImpl(client, InMemoryTokenStorage(), baseUrl = DEFAULT_BASE_URL)

        assertThrowsSuspend<com.homelab.household.domain.exception.DomainException> { repo.checkStatus() }
    }

    @Test
    fun concurrent_401_requests_trigger_single_flight_refresh_mutex() = runTest {
        val refreshCount = AtomicInteger(0)

        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/refresh" -> {
                    refreshCount.incrementAndGet()
                    respond(
                        content = """{
                            "access_token": "new-jwt-token-456",
                            "token_type": "bearer",
                            "user": {
                                "id": "u-1",
                                "username": "alice",
                                "email": "alice@homelab.local",
                                "full_name": "Alice Doe",
                                "is_admin": true,
                                "is_active": true,
                                "created_at": "2026-09-08T00:00:00Z"
                            }
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val tokenStorage = InMemoryTokenStorage()
        tokenStorage.saveTokens("expired-token", "refresh-token")
        val repo = AuthRepositoryImpl(client, tokenStorage, baseUrl = DEFAULT_BASE_URL)

        // Launch 5 parallel refresh operations
        val jobs = (1..5).map {
            async { repo.refreshToken() }
        }
        val results = jobs.awaitAll()

        assertEquals(5, results.size)
        // Verify exactly 1 refresh HTTP call was dispatched across all 5 threads!
        assertEquals(1, refreshCount.get())
        assertEquals("new-jwt-token-456", tokenStorage.getAccessToken())
    }
}
