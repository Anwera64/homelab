package com.homelab.household.data.datasource.remote

import com.homelab.household.data.di.DEFAULT_BASE_URL
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
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The wire for the household's milestones — what the agents have noticed and reported. */
class KtorGossipRemoteDataSourceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val milestoneJson = """{
        "id": "ms-1",
        "source_user_id": "emma",
        "source_username": "Emma",
        "reporting_agent_id": "a-1",
        "reporting_agent_name": "Llama",
        "target_scope": "household",
        "category": "milestone",
        "summary": "Emma finished her first 5k run",
        "details": {},
        "is_active": true,
        "created_at": "2026-09-13T00:00:00Z"
    }"""

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(content, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun dataSource(engine: MockEngine) = KtorGossipRemoteDataSource(
        client = HttpClient(engine) { install(ContentNegotiation) { json(json) } },
        baseUrl = DEFAULT_BASE_URL,
    )

    private fun unreachableHub() = MockEngine { throw IOException("Connection refused") }

    // ---- listHouseholdMilestones --------------------------------------------

    @Test
    fun `GIVEN a household with a milestone WHEN the shared feed is asked for THEN it comes back with the limit sent`() = runTest {
        // GIVEN
        var path: String? = null
        var limit: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            limit = request.url.parameters["limit"]
            respondJson("[$milestoneJson]")
        }

        // WHEN
        val milestones = dataSource(engine).listHouseholdMilestones(20)

        // THEN
        assertEquals("/api/v1/gossip/household", path)
        assertEquals("20", limit)
        assertEquals(1, milestones.size)
        assertEquals("ms-1", milestones.first().id)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN the household's milestones are asked for THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).listHouseholdMilestones(20) }
    }

    // ---- listUserAuditMilestones --------------------------------------------

    @Test
    fun `GIVEN a member auditing their own trail WHEN it is asked for THEN it comes back from the audit address with the limit sent`() = runTest {
        // GIVEN
        var path: String? = null
        var limit: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            limit = request.url.parameters["limit"]
            respondJson("[$milestoneJson]")
        }

        // WHEN
        val milestones = dataSource(engine).listUserAuditMilestones(50)

        // THEN
        assertEquals("/api/v1/gossip/audit", path)
        assertEquals("50", limit)
        assertEquals(1, milestones.size)
    }

    // ---- revokeMilestone ------------------------------------------------------

    @Test
    fun `GIVEN a milestone a member wants forgotten WHEN it is revoked THEN its own address is asked to remove it`() = runTest {
        // GIVEN
        var path: String? = null
        var method: HttpMethod? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            method = request.method
            respondJson("""{"message": "Milestone revoked"}""")
        }

        // WHEN
        dataSource(engine).revokeMilestone("ms-1")

        // THEN
        assertEquals("/api/v1/gossip/ms-1", path)
        assertEquals(HttpMethod.Delete, method)
    }

    @Test
    fun `GIVEN the hub cannot be reached WHEN a milestone is revoked THEN it is reported as offline`() = runTest {
        // GIVEN
        val engine = unreachableHub()

        // WHEN / THEN
        assertFailsWith<ServerOfflineException> { dataSource(engine).revokeMilestone("ms-1") }
    }
}
