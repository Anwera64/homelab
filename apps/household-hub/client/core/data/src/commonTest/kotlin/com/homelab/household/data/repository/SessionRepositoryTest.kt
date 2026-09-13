package com.homelab.household.data.repository

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.ChatStreamEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import io.ktor.utils.io.errors.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun stream_chat_turn_streams_sse_events() = runTest {
        val sseResponse = """
            data: {"type": "delta", "content": "Working on it..."}

            data: {"type": "done", "message_id": "m2", "assistant_content": "Working on it...", "agent_name": "Assistant"}

            data: [DONE]

        """.trimIndent()

        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sessions/s-1/chat/stream" -> {
                    respond(
                        content = sseResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
                    )
                }
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val repo = SessionRepositoryImpl(client, baseUrl = DEFAULT_BASE_URL)
        val events = repo.streamChatTurn("s-1", "Hello").toList()

        assertEquals(2, events.size)
        assertTrue(events[0] is ChatStreamEvent.Delta)
        assertEquals("Working on it...", (events[0] as ChatStreamEvent.Delta).content)
        assertTrue(events[1] is ChatStreamEvent.Done)
    }

    @Test
    fun stream_chat_turn_on_409_conflict_polls_and_recovers_messages() = runTest {
        var pollCount = 0
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sessions/s-1/chat/stream" -> {
                    respond(
                        content = """{"detail": "Session s-1 is currently processing another message."}""",
                        status = HttpStatusCode.Conflict,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/api/v1/sessions/s-1" -> {
                    pollCount++
                    if (pollCount == 1) {
                        // First poll: still running, only user message present
                        respond(
                            content = """{"id": "s-1", "user_id": "u-1", "title": "Test", "is_secret": false, "messages": [{"id": "m1", "session_id": "s-1", "role": "user", "content": "Hello", "created_at": "2026-09-08T00:00:00Z"}]}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    } else {
                        // Second poll: completed, assistant message present
                        respond(
                            content = """{"id": "s-1", "user_id": "u-1", "title": "Test", "is_secret": false, "messages": [
                                {"id": "m1", "session_id": "s-1", "role": "user", "content": "Hello", "created_at": "2026-09-08T00:00:00Z"},
                                {"id": "m2", "session_id": "s-1", "role": "assistant", "content": "Recovered response", "created_at": "2026-09-08T00:00:02Z"}
                            ]}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val repo = SessionRepositoryImpl(client, baseUrl = DEFAULT_BASE_URL, pollDelayMs = 10)
        val events = repo.streamChatTurn("s-1", "Hello").toList()

        assertTrue(events.isNotEmpty())
        val doneEvent = events.last()
        assertTrue(doneEvent is ChatStreamEvent.Done)
        assertEquals("Recovered response", (doneEvent as ChatStreamEvent.Done).assistantContent)
        assertTrue(pollCount >= 2)
    }

    @Test
    fun stream_chat_turn_when_server_offline_throws_server_offline_exception() = runTest {
        val mockEngine = MockEngine {
            throw IOException("Connection refused")
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val repo = SessionRepositoryImpl(client, baseUrl = DEFAULT_BASE_URL)

        assertFailsWith<ServerOfflineException> {
            repo.streamChatTurn("s-1", "Hello").toList()
        }
    }

    @Test
    fun approve_tool_proposal_posts_approval_and_returns_true() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/sessions/s-1/tools/approve") {
                respond(
                    content = """{"status": "approved", "tool_call_id": "tc-1", "result": {"success": true}}""",
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

        val repo = SessionRepositoryImpl(client, baseUrl = DEFAULT_BASE_URL)
        val success = repo.approveToolProposal("s-1", "tc-1", approved = true)

        assertTrue(success)
    }

    @Test
    fun get_session_returns_session_and_messages_from_detail_endpoint() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/sessions/s-100") {
                respond(
                    content = """{
                        "id": "s-100",
                        "user_id": "u-1",
                        "agent_id": "a-1",
                        "title": "Kitchen Planning",
                        "is_secret": false,
                        "is_archived": false,
                        "messages": [
                            {"id": "m-1", "session_id": "s-100", "role": "user", "content": "What's for dinner?", "created_at": "2026-09-08T18:00:00Z"},
                            {"id": "m-2", "session_id": "s-100", "role": "assistant", "content": "How about pasta?", "created_at": "2026-09-08T18:00:01Z"}
                        ]
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

        val repo = SessionRepositoryImpl(client, baseUrl = DEFAULT_BASE_URL)
        val (session, messages) = repo.getSession("s-100")

        assertEquals("s-100", session.id)
        assertEquals("Kitchen Planning", session.title)
        assertEquals(2, messages.size)
        assertEquals("m-1", messages[0].id)
        assertEquals("m-2", messages[1].id)
    }

    @Test
    fun toggle_secret_mode_calls_patch_secret_endpoint() = runTest {
        var patchedPath: String? = null
        val mockEngine = MockEngine { request ->
            patchedPath = request.url.encodedPath
            respond(
                content = """{"id": "s-100", "user_id": "u-1", "title": "Secret Chat", "is_secret": true, "is_archived": false}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val repo = SessionRepositoryImpl(client, baseUrl = DEFAULT_BASE_URL)
        val result = repo.toggleSecretMode("s-100", isSecret = true)

        assertEquals("/api/v1/sessions/s-100/secret", patchedPath)
        assertTrue(result.isSecret)
    }

    @Test
    fun archive_session_calls_post_archive_endpoint() = runTest {
        var postedPath: String? = null
        val mockEngine = MockEngine { request ->
            postedPath = request.url.encodedPath
            respond(
                content = """{"id": "s-100", "user_id": "u-1", "title": "Secret Chat", "is_secret": false, "is_archived": true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val repo = SessionRepositoryImpl(client, baseUrl = DEFAULT_BASE_URL)
        repo.archiveSession("s-100")

        assertEquals("/api/v1/sessions/s-100/archive", postedPath)
    }

    @Test
    fun retry_message_resubmits_message_content_via_stream() = runTest {
        val sseResponse = """
            data: {"type": "delta", "content": "Retried response"}

            data: {"type": "done", "message_id": "m-retry", "assistant_content": "Retried response", "agent_name": "Assistant"}

            data: [DONE]

        """.trimIndent()

        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sessions/s-100" -> {
                    respond(
                        content = """{
                            "id": "s-100",
                            "user_id": "u-1",
                            "title": "Kitchen Planning",
                            "is_secret": false,
                            "is_archived": false,
                            "messages": [
                                {"id": "m-retry-prompt", "session_id": "s-100", "role": "user", "content": "Hello retry", "created_at": "2026-09-08T18:00:00Z"}
                            ]
                        }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/api/v1/sessions/s-100/chat/stream" -> {
                    respond(
                        content = sseResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
                    )
                }
                else -> respond("Not Found", HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val repo = SessionRepositoryImpl(client, baseUrl = DEFAULT_BASE_URL)
        // Populate cache
        repo.getSession("s-100")

        val events = repo.retryMessage("m-retry-prompt").toList()
        assertEquals(2, events.size)
        assertTrue(events[0] is ChatStreamEvent.Delta)
        assertEquals("Retried response", (events[0] as ChatStreamEvent.Delta).content)
    }
}
