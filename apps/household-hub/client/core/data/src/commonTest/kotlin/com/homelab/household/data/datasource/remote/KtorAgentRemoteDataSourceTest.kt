package com.homelab.household.data.datasource.remote

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.data.dto.AgentCreateDto
import com.homelab.household.data.dto.AgentUpdateDto
import com.homelab.household.domain.exception.ServerOfflineException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The wire for everything an admin does to the household's agents. */
class KtorAgentRemoteDataSourceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val llamaJson = """{
        "id": "a-1",
        "slug": "llama",
        "name": "Llama",
        "description": "General helper",
        "avatar": "🦙",
        "system_prompt": "You are helpful.",
        "model_alias": "qwen3:14b",
        "temperature": 0.7,
        "top_p": 0.9,
        "tool_permissions": ["search"],
        "is_builtin": false,
        "is_active": true,
        "owner_id": "emma",
        "created_at": "2026-09-13T00:00:00Z"
    }"""

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(content, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun dataSource(engine: MockEngine) = KtorAgentRemoteDataSource(
        client = HttpClient(engine) { install(ContentNegotiation) { json(json) } },
        baseUrl = DEFAULT_BASE_URL,
    )

    private fun assertSameJson(expected: String, actual: String?) =
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual ?: "null"))

    private fun unreachableHub() = MockEngine { throw IOException("Connection refused") }

    // ---- listAgents ---------------------------------------------------------

    @Test
    fun `GIVEN a household with one agent WHEN its agents are asked for THEN their full personality comes back`() = runTest {
        // GIVEN
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respondJson("[$llamaJson]")
        }

        // WHEN
        val agents = dataSource(engine).listAgents()

        // THEN
        assertEquals("/api/v1/agents", path)
        assertEquals(1, agents.size)
        assertEquals("a-1", agents.first().id)
        assertEquals("llama", agents.first().slug)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN the household's agents are asked for THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).listAgents() }
    }

    // ---- getAgent -------------------------------------------------------------

    @Test
    fun `GIVEN one agent's id WHEN it is asked for by itself THEN its personality comes back`() = runTest {
        // GIVEN
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respondJson(llamaJson)
        }

        // WHEN
        val agent = dataSource(engine).getAgent("a-1")

        // THEN
        assertEquals("/api/v1/agents/a-1", path)
        assertEquals("Llama", agent.name)
    }

    // ---- createAgent ------------------------------------------------------

    @Test
    fun `GIVEN a new agent's personality WHEN it is created THEN it is posted and the stored agent comes back`() = runTest {
        // GIVEN
        var path: String? = null
        var sent: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            sent = (request.body as TextContent).text
            respondJson(llamaJson, HttpStatusCode.Created)
        }
        val create = AgentCreateDto(
            slug = "llama",
            name = "Llama",
            description = "General helper",
            avatar = "🦙",
            system_prompt = "You are helpful.",
            model_alias = "llama3:8b",
            temperature = 0.5f,
            top_p = 0.95f,
            tool_permissions = listOf("search"),
        )

        // WHEN
        val agent = dataSource(engine).createAgent(create)

        // THEN
        assertEquals("/api/v1/agents", path)
        assertSameJson(
            """{"slug": "llama", "name": "Llama", "description": "General helper", "avatar": "🦙",
                "system_prompt": "You are helpful.", "model_alias": "llama3:8b", "temperature": 0.5,
                "top_p": 0.95, "tool_permissions": ["search"]}""",
            sent,
        )
        assertEquals("a-1", agent.id)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN an agent is created THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()
        val create = AgentCreateDto(slug = "llama", name = "Llama", system_prompt = "You are helpful.")

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).createAgent(create) }
    }

    // ---- updateAgent --------------------------------------------------------

    @Test
    fun `GIVEN changes to an agent's personality WHEN it is updated THEN they are sent to that agent's own address`() = runTest {
        // GIVEN
        var path: String? = null
        var method: HttpMethod? = null
        var sent: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            method = request.method
            sent = (request.body as TextContent).text
            respondJson(llamaJson)
        }
        val update = AgentUpdateDto(name = "Llama", temperature = 0.5f, is_active = true)

        // WHEN
        val agent = dataSource(engine).updateAgent("a-1", update)

        // THEN
        assertEquals("/api/v1/agents/a-1", path)
        assertEquals(HttpMethod.Put, method)
        assertSameJson("""{"name": "Llama", "temperature": 0.5, "is_active": true}""", sent)
        assertEquals("a-1", agent.id)
    }

    // ---- deleteAgent --------------------------------------------------------

    @Test
    fun `GIVEN an agent an admin no longer wants WHEN it is deleted THEN its own address is asked to remove it`() = runTest {
        // GIVEN
        var path: String? = null
        var method: HttpMethod? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            method = request.method
            respondJson("""{"message": "Agent deleted"}""")
        }

        // WHEN
        dataSource(engine).deleteAgent("a-1")

        // THEN
        assertEquals("/api/v1/agents/a-1", path)
        assertEquals(HttpMethod.Delete, method)
    }

    // ---- restoreAgent -------------------------------------------------------

    @Test
    fun `GIVEN a deleted agent WHEN it is restored THEN its own restore address is posted to and it comes back`() = runTest {
        // GIVEN
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respondJson(llamaJson)
        }

        // WHEN
        val agent = dataSource(engine).restoreAgent("a-1")

        // THEN
        assertEquals("/api/v1/agents/a-1/restore", path)
        assertEquals("a-1", agent.id)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN a deleted agent is restored THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).restoreAgent("a-1") }
    }
}
