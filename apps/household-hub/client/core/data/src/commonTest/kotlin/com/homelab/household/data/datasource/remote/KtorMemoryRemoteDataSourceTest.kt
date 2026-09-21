package com.homelab.household.data.datasource.remote

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.data.dto.MemoryUpdateDto
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
import kotlin.test.assertNull

/** The wire for what the agents remember about the household and its members. */
class KtorMemoryRemoteDataSourceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val memoryJson = """{
        "id": "mem-1",
        "user_id": "emma",
        "agent_id": "a-1",
        "content": "Emma prefers tea over coffee",
        "category": "fact",
        "scope": "personal",
        "confidence": 0.9,
        "is_active": true,
        "created_at": "2026-09-13T00:00:00Z"
    }"""

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(content, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun dataSource(engine: MockEngine) = KtorMemoryRemoteDataSource(
        client = HttpClient(engine) { install(ContentNegotiation) { json(json) } },
        baseUrl = DEFAULT_BASE_URL,
    )

    private fun assertSameJson(expected: String, actual: String?) =
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual ?: "null"))

    private fun unreachableHub() = MockEngine { throw IOException("Connection refused") }

    // ---- auditMemories ------------------------------------------------------

    @Test
    fun `GIVEN a scope to audit WHEN memories are asked for THEN it is sent as a query parameter`() = runTest {
        // GIVEN
        var path: String? = null
        var query: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            query = request.url.parameters["scope"]
            respondJson("[$memoryJson]")
        }

        // WHEN
        val memories = dataSource(engine).auditMemories("personal")

        // THEN
        assertEquals("/api/v1/memories", path)
        assertEquals("personal", query)
        assertEquals(1, memories.size)
        assertEquals("mem-1", memories.first().id)
    }

    @Test
    fun `GIVEN no scope WHEN every memory is audited THEN no scope parameter is sent`() = runTest {
        // GIVEN
        var query: String? = null
        var hadQuery = true
        val engine = MockEngine { request ->
            hadQuery = request.url.parameters.contains("scope")
            query = request.url.parameters["scope"]
            respondJson("[$memoryJson]")
        }

        // WHEN
        dataSource(engine).auditMemories(null)

        // THEN
        assertEquals(false, hadQuery)
        assertNull(query)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN memories are audited THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).auditMemories(null) }
    }

    // ---- deleteMemory -------------------------------------------------------

    @Test
    fun `GIVEN a memory nobody wants kept WHEN it is deleted THEN its own address is asked to remove it`() = runTest {
        // GIVEN
        var path: String? = null
        var method: HttpMethod? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            method = request.method
            respondJson("""{"message": "Memory deleted"}""")
        }

        // WHEN
        dataSource(engine).deleteMemory("mem-1")

        // THEN
        assertEquals("/api/v1/memories/mem-1", path)
        assertEquals(HttpMethod.Delete, method)
    }

    // ---- updateMemory ---------------------------------------------------------

    @Test
    fun `GIVEN changes to a memory WHEN it is updated THEN they are patched to that memory's own address`() = runTest {
        // GIVEN
        var path: String? = null
        var method: HttpMethod? = null
        var sent: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            method = request.method
            sent = (request.body as TextContent).text
            respondJson(memoryJson)
        }
        val update = MemoryUpdateDto(content = "Emma prefers tea", confidence = 0.95f, is_active = true)

        // WHEN
        val memory = dataSource(engine).updateMemory("mem-1", update)

        // THEN
        assertEquals("/api/v1/memories/mem-1", path)
        assertEquals(HttpMethod.Patch, method)
        assertSameJson("""{"content": "Emma prefers tea", "confidence": 0.95, "is_active": true}""", sent)
        assertEquals("mem-1", memory.id)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN a memory is updated THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> {
            dataSource(engine).updateMemory("mem-1", MemoryUpdateDto(content = "Emma prefers tea"))
        }
    }
}
