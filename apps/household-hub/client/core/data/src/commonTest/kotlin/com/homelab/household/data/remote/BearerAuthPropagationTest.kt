package com.homelab.household.data.remote

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.data.local.InMemoryTokenStorage
import com.homelab.household.data.repository.AgentRepositoryImpl
import com.homelab.household.data.repository.GossipRepositoryImpl
import com.homelab.household.data.repository.SpaceRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BearerAuthPropagationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun client_with_bearer_auth_propagates_token_to_remote_repositories() = runTest {
        val capturedHeaders = mutableListOf<String?>()
        val tokenStorage = InMemoryTokenStorage()
        tokenStorage.saveTokens(accessToken = "bearer-secret-token-abc")

        val mockEngine = MockEngine { request ->
            capturedHeaders.add(request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v1/spaces/shared" -> {
                    respond(
                        content = """{"id": "space-1", "name": "Family", "type": "shared"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/api/v1/agents" -> {
                    respond(
                        content = """[{"id": "a-1", "slug": "llama", "name": "Llama", "system_prompt": "You are helpful."}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
            install(Auth) {
                bearer {
                    loadTokens {
                        tokenStorage.getAccessToken()?.let { BearerTokens(accessToken = it, refreshToken = "") }
                    }
                    sendWithoutRequest { request ->
                        !request.url.buildString().contains("/auth/")
                    }
                }
            }
        }

        val spaceRepo = SpaceRepositoryImpl(client, baseUrl = DEFAULT_BASE_URL)
        val agentRepo = AgentRepositoryImpl(client, baseUrl = DEFAULT_BASE_URL)

        spaceRepo.getHouseholdSpace()
        agentRepo.listAgents()

        assertEquals(2, capturedHeaders.size)
        assertEquals("Bearer bearer-secret-token-abc", capturedHeaders[0])
        assertEquals("Bearer bearer-secret-token-abc", capturedHeaders[1])
    }
}
