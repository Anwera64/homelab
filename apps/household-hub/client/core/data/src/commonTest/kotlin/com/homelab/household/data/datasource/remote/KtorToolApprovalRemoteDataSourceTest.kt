package com.homelab.household.data.datasource.remote

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.data.dto.ToolApprovalDto
import com.homelab.household.data.dto.ToolApprovalUpdateDto
import com.homelab.household.data.repository.ToolApprovalRepositoryImpl
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolApproval
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The wire for which writes agents do without asking: `GET` and `PUT /users/me/tool-approvals`. */
class KtorToolApprovalRemoteDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val listed = """[
        {"tool": "calendar_write", "action": "create", "auto": true, "always_asks": false},
        {"tool": "calendar_write", "action": "delete", "auto": false, "always_asks": true},
        {"tool": "calendar_write", "action": "teleport", "auto": false, "always_asks": false}
    ]"""

    private fun dataSource(engine: MockEngine) =
        KtorToolApprovalRemoteDataSource(
            client = HttpClient(engine) { install(ContentNegotiation) { json(json) } },
            baseUrl = DEFAULT_BASE_URL,
        )

    private fun respondingWith(onRequest: (method: HttpMethod, path: String, body: String?) -> Unit = { _, _, _ -> }) =
        MockEngine { request ->
            onRequest(request.method, request.url.encodedPath, (request.body as? TextContent)?.text)
            respond(listed, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }

    @Test
    fun `GIVEN the member's settings WHEN listed THEN they come from their own address`() =
        runTest {
            var asked: Pair<HttpMethod, String>? = null
            val rows = dataSource(respondingWith { method, path, _ -> asked = method to path }).getToolApprovals()

            assertEquals(HttpMethod.Get to "/api/v1/users/me/tool-approvals", asked)
            assertEquals(ToolApprovalDto("calendar_write", "create", auto = true, alwaysAsks = false), rows.first())
        }

    @Test
    fun `GIVEN adding events WHEN made automatic THEN the hub is sent its tool and action and choice`() =
        runTest {
            var sent: Triple<HttpMethod, String, String?>? = null
            dataSource(respondingWith { method, path, body -> sent = Triple(method, path, body) })
                .setToolApproval(ToolApprovalUpdateDto("calendar_write", "create", auto = true))

            assertEquals(HttpMethod.Put, sent?.first)
            assertEquals("/api/v1/users/me/tool-approvals", sent?.second)
            assertEquals(
                Json.parseToJsonElement("""{"tool": "calendar_write", "action": "create", "auto": true}"""),
                Json.parseToJsonElement(sent?.third ?: "null"),
            )
        }

    @Test
    fun `GIVEN the hub's list WHEN mapped THEN each write keeps its choice and an action this phone doesn't know is left out`() =
        runTest {
            val repository = ToolApprovalRepositoryImpl(dataSource(respondingWith()))

            assertEquals(
                listOf(
                    ToolApproval("calendar_write", ToolAction.Create, automatic = true),
                    ToolApproval("calendar_write", ToolAction.Delete, automatic = false, alwaysAsks = true),
                ),
                repository.setToolApproval("calendar_write", ToolAction.Create, automatic = true),
            )
        }

    @Test
    fun `GIVEN no hub WHEN listed THEN it says the hub is offline`() =
        runTest {
            val engine = MockEngine { throw IOException("Connection refused") }

            assertFailsWith<ServerOfflineException> { dataSource(engine).getToolApprovals() }
        }
}
