package com.homelab.household.data.datasource.remote

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.SessionConflictException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.model.ChatStreamEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The wire for conversations, including the streamed one. The 409 the hub returns when it is
 * already working on a turn becomes [SessionConflictException] here and stops being an HTTP status
 * anybody above has to know about — recovering from it is the repository's decision, not this
 * file's.
 */
class KtorSessionRemoteDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(content, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.respondSse(content: String): HttpResponseData =
        respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))

    private fun dataSource(engine: MockEngine) =
        KtorSessionRemoteDataSource(
            client = HttpClient(engine) { install(ContentNegotiation) { json(json) } },
            baseUrl = DEFAULT_BASE_URL,
        )

    private fun assertSameJson(
        expected: String,
        actual: String?,
    ) = assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual ?: "null"))

    private fun unreachableHub() = MockEngine { throw IOException("Connection refused") }

    // ---- reading conversations --------------------------------------------

    @Test
    fun `GIVEN a household with two conversations WHEN they are listed THEN both come back`() =
        runTest {
            // GIVEN
            var path: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respondJson(
                        """[
                    {"id": "s-1", "user_id": "u-1", "title": "Dinner", "is_secret": false},
                    {"id": "s-2", "user_id": "u-1", "title": "Diary", "is_secret": true}
                ]""",
                    )
                }

            // WHEN
            val sessions = dataSource(engine).listSessions()

            // THEN
            assertEquals("/api/v1/sessions", path)
            assertEquals(listOf("s-1", "s-2"), sessions.map { it.id })
            assertEquals(listOf(false, true), sessions.map { it.is_secret })
        }

    @Test
    fun `GIVEN a conversation with messages WHEN it is fetched THEN the conversation and its messages come back`() =
        runTest {
            // GIVEN
            var path: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respondJson(
                        """{
                    "id": "s-100", "user_id": "u-1", "agent_id": "a-1", "title": "Kitchen Planning",
                    "is_secret": false, "is_archived": false,
                    "messages": [
                        {"id": "m-1", "session_id": "s-100", "role": "user", "content": "What's for dinner?", "created_at": "2026-09-08T18:00:00Z"},
                        {"id": "m-2", "session_id": "s-100", "role": "assistant", "content": "How about pasta?", "created_at": "2026-09-08T18:00:01Z"}
                    ]
                }""",
                    )
                }

            // WHEN
            val detail = dataSource(engine).fetchSession("s-100")

            // THEN
            assertEquals("/api/v1/sessions/s-100", path)
            assertEquals("Kitchen Planning", detail.title)
            assertEquals(listOf("m-1", "m-2"), detail.messages.map { it.id })
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN conversations are listed THEN it is reported as offline`() =
        runTest {
            // GIVEN
            val engine = unreachableHub()

            // WHEN / THEN
            assertFailsWith<ServerOfflineException> { dataSource(engine).listSessions() }
        }

    // ---- changing conversations -------------------------------------------

    @Test
    fun `GIVEN an agent to talk to WHEN a conversation is started THEN the agent title and secrecy are posted`() =
        runTest {
            // GIVEN
            var path: String? = null
            var sent: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    sent = (request.body as TextContent).text
                    respondJson(
                        """{"id": "s-3", "user_id": "u-1", "agent_id": "a-1", "title": "Groceries", "is_secret": true}""",
                    )
                }

            // WHEN
            val session = dataSource(engine).createSession(agentId = "a-1", title = "Groceries", isSecret = true)

            // THEN
            assertEquals("/api/v1/sessions", path)
            assertSameJson("""{"agent_id": "a-1", "title": "Groceries", "is_secret": true}""", sent)
            assertEquals("s-3", session.id)
        }

    @Test
    fun `GIVEN an open conversation WHEN it is archived THEN the hub is asked to archive it`() =
        runTest {
            // GIVEN
            var path: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respondJson("""{"id": "s-100", "user_id": "u-1", "title": "Dinner", "is_archived": true}""")
                }

            // WHEN
            dataSource(engine).archiveSession("s-100")

            // THEN
            assertEquals("/api/v1/sessions/s-100/archive", path)
        }

    @Test
    fun `GIVEN an open conversation WHEN it is made secret THEN the new secrecy is patched and comes back`() =
        runTest {
            // GIVEN
            var path: String? = null
            var sent: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    sent = (request.body as TextContent).text
                    respondJson("""{"id": "s-100", "user_id": "u-1", "title": "Secret Chat", "is_secret": true}""")
                }

            // WHEN
            val session = dataSource(engine).toggleSecretMode("s-100", isSecret = true)

            // THEN
            assertEquals("/api/v1/sessions/s-100/secret", path)
            assertSameJson("""{"is_secret": true}""", sent)
            assertTrue(session.is_secret)
        }

    @Test
    fun `GIVEN a conversation nobody wants kept WHEN it is deleted THEN the hub is asked to delete it`() =
        runTest {
            // GIVEN
            var path: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respondJson("""{"message": "deleted"}""")
                }

            // WHEN
            dataSource(engine).deleteSession("s-100")

            // THEN
            assertEquals("/api/v1/sessions/s-100", path)
        }

    // ---- tool approvals ----------------------------------------------------

    @Test
    fun `GIVEN an agent waiting on a tool WHEN the tool is approved THEN the approval is posted and accepted`() =
        runTest {
            // GIVEN
            var path: String? = null
            var sent: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    sent = (request.body as TextContent).text
                    respondJson("""{"status": "approved", "tool_call_id": "tc-1", "result": {"success": true}}""")
                }

            // WHEN
            val accepted = dataSource(engine).approveToolProposal("s-1", "tc-1", approved = true)

            // THEN
            assertEquals("/api/v1/sessions/s-1/tools/approve", path)
            assertSameJson("""{"tool_call_id": "tc-1", "approved": true}""", sent)
            assertTrue(accepted)
        }

    // ---- the streamed turn -------------------------------------------------

    @Test
    fun `GIVEN a hub streaming a reply WHEN a turn is sent THEN each event arrives in order`() =
        runTest {
            // GIVEN
            val sse =
                """
                data: {"type": "delta", "content": "Working on it..."}

                data: {"type": "done", "message_id": "m2", "assistant_content": "Working on it...", "agent_name": "Assistant"}

                data: [DONE]

                """.trimIndent()
            var path: String? = null
            var sent: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    sent = (request.body as TextContent).text
                    respondSse(sse)
                }

            // WHEN
            val events = dataSource(engine).openChatStream("s-1", "Hello", autoApproveWrites = true).toList()

            // THEN � auto_approve_writes is sent as true rather than false on purpose: it defaults to
            // false in the DTO, and kotlinx.serialization omits a field equal to its default, so a
            // false here would assert nothing about whether the flag reaches the hub at all.
            assertEquals("/api/v1/sessions/s-1/chat/stream", path)
            assertSameJson("""{"content": "Hello", "auto_approve_writes": true}""", sent)
            assertEquals(2, events.size)
            assertEquals("Working on it...", (events[0] as ChatStreamEvent.Delta).content)
            assertTrue(events[1] is ChatStreamEvent.Done)
        }

    /**
     * The one refusal this endpoint has that is not a failure: the hub is already working on a turn
     * for this conversation. It becomes a named domain exception so the repository can recognise it
     * and go looking for the answer, instead of matching on an HTTP status.
     */
    @Test
    fun `GIVEN the hub already working on this conversation WHEN another turn is sent THEN it is refused as a conflict`() =
        runTest {
            // GIVEN
            val engine =
                MockEngine {
                    respondJson(
                        """{"detail": "Session s-1 is currently processing another message."}""",
                        HttpStatusCode.Conflict,
                    )
                }

            // WHEN / THEN
            assertFailsWith<SessionConflictException> {
                dataSource(engine).openChatStream("s-1", "Hello", autoApproveWrites = false).toList()
            }
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN a turn is sent THEN it is reported as offline`() =
        runTest {
            // GIVEN
            val engine = unreachableHub()

            // WHEN / THEN
            assertFailsWith<ServerOfflineException> {
                dataSource(engine).openChatStream("s-1", "Hello", autoApproveWrites = false).toList()
            }
        }

    @Test
    fun `GIVEN the hub answering a turn with an unexpected status WHEN it is sent THEN it is reported as a bad answer from upstream`() =
        runTest {
            // GIVEN
            val engine = MockEngine { respondJson("""{"detail": "teapot"}""", HttpStatusCode.InternalServerError) }

            // WHEN / THEN
            val thrown =
                assertFailsWith<UpstreamGatewayException> {
                    dataSource(engine).openChatStream("s-1", "Hello", autoApproveWrites = false).toList()
                }
            assertEquals(500, thrown.statusCode)
        }
}
