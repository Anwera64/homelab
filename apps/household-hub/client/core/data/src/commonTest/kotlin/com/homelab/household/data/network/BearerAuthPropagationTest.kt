package com.homelab.household.data.network

import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.data.datasource.remote.KtorAgentRemoteDataSource
import com.homelab.household.data.datasource.remote.KtorSpaceRemoteDataSource
import com.homelab.household.data.di.DEFAULT_BASE_URL
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * The Auth plugin, not a data source, is what carries this phone's token onto an outgoing call —
 * so it is proven once here, against two unrelated data sources, rather than in every test file.
 */
class BearerAuthPropagationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `GIVEN a phone with a kept token WHEN two different calls reach the hub THEN both carry it as a bearer header`() = runTest {
        // GIVEN
        val capturedHeaders = mutableListOf<String?>()
        val storage = InMemorySessionStorage()
        storage.saveTokens(accessToken = "bearer-secret-token-abc")

        val mockEngine = MockEngine { request ->
            capturedHeaders.add(request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v1/spaces/shared" -> respond(
                    content = """{"id": "space-1", "name": "Family", "type": "shared"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/api/v1/agents" -> respond(
                    content = """[{"id": "a-1", "slug": "llama", "name": "Llama", "system_prompt": "You are helpful."}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
            install(Auth) {
                bearer {
                    loadTokens {
                        storage.getAccessToken()?.let { BearerTokens(accessToken = it, refreshToken = "") }
                    }
                    sendWithoutRequest { request -> !request.url.buildString().contains("/auth/") }
                }
            }
        }

        val spaceDataSource = KtorSpaceRemoteDataSource(client, baseUrl = DEFAULT_BASE_URL)
        val agentDataSource = KtorAgentRemoteDataSource(client, baseUrl = DEFAULT_BASE_URL)

        // WHEN
        spaceDataSource.getHouseholdSpace()
        agentDataSource.listAgents()

        // THEN
        assertEquals(2, capturedHeaders.size)
        assertEquals("Bearer bearer-secret-token-abc", capturedHeaders[0])
        assertEquals("Bearer bearer-secret-token-abc", capturedHeaders[1])
    }
}
