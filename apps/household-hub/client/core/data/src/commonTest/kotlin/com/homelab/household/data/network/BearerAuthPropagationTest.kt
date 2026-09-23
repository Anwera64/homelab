package com.homelab.household.data.network

import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.data.datasource.remote.KtorAgentRemoteDataSource
import com.homelab.household.data.datasource.remote.KtorSpaceRemoteDataSource
import com.homelab.household.data.di.DEFAULT_BASE_URL
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
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [installBearerAuth], not a data source, is what carries this phone's token onto an outgoing call —
 * so it is proven once here, against the same configuration the app installs, rather than in every
 * test file.
 */
class BearerAuthPropagationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `GIVEN a phone with a kept token WHEN two different calls reach the hub THEN both carry it as a bearer header`() =
        runTest {
            // GIVEN
            val capturedHeaders = mutableListOf<String?>()
            val storage = InMemorySessionStorage()
            storage.saveTokens(accessToken = "bearer-secret-token-abc")

            val mockEngine =
                MockEngine { request ->
                    capturedHeaders.add(request.headers[HttpHeaders.Authorization])
                    when (request.url.encodedPath) {
                        "/api/v1/spaces/shared" -> {
                            respond(
                                content = """{"id": "space-1", "name": "Family", "type": "shared"}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        "/api/v1/agents" -> {
                            respond(
                                content = ONE_AGENT_JSON,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        else -> {
                            respond("Not Found", HttpStatusCode.NotFound)
                        }
                    }
                }

            val client = clientWith(mockEngine, storage)

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

    @Test
    fun `GIVEN a call made as one member WHEN another member's token is kept THEN the next call carries the new token`() =
        runTest {
            // GIVEN
            val capturedHeaders = mutableListOf<String?>()
            val storage = InMemorySessionStorage()
            val client = clientWith(sharedSpaceHub(capturedHeaders), storage)
            val spaces = KtorSpaceRemoteDataSource(client, baseUrl = DEFAULT_BASE_URL)
            storage.saveTokens(accessToken = "first-member-token")
            spaces.getHouseholdSpace()

            // WHEN
            storage.saveTokens(accessToken = "second-member-token")
            spaces.getHouseholdSpace()

            // THEN
            assertEquals(listOf<String?>("Bearer first-member-token", "Bearer second-member-token"), capturedHeaders)
        }

    @Test
    fun `GIVEN the kept session is cleared WHEN a call is made THEN it carries no bearer header`() =
        runTest {
            // GIVEN
            val capturedHeaders = mutableListOf<String?>()
            val storage = InMemorySessionStorage()
            val client = clientWith(sharedSpaceHub(capturedHeaders), storage)
            val spaces = KtorSpaceRemoteDataSource(client, baseUrl = DEFAULT_BASE_URL)
            storage.saveTokens(accessToken = "signed-out-member-token")
            spaces.getHouseholdSpace()

            // WHEN
            storage.clear()
            spaces.getHouseholdSpace()

            // THEN
            assertEquals(listOf<String?>("Bearer signed-out-member-token", null), capturedHeaders)
        }

    private fun clientWith(
        engine: MockEngine,
        storage: InMemorySessionStorage,
    ) = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        installBearerAuth(storage)
    }

    /** A hub that only knows the shared space, noting the Authorization header of every call. */
    private fun sharedSpaceHub(capturedHeaders: MutableList<String?>) =
        MockEngine { request ->
            capturedHeaders.add(request.headers[HttpHeaders.Authorization])
            respond(
                content = """{"id": "space-1", "name": "Family", "type": "shared"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
}

/** The agent list the fixture hub answers with; its shape is irrelevant here, only that it parses. */
private const val ONE_AGENT_JSON =
    """[{"id": "a-1", "slug": "llama", "name": "Llama", "system_prompt": "You are helpful."}]"""
