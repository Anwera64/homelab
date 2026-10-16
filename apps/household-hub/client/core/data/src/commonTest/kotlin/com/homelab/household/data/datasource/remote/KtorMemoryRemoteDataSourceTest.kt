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

/** The wire for the memory page: the list, ✕ and Undo. */
class KtorMemoryRemoteDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val pageJson = """{
        "yours": [{
            "id": "mem-1",
            "content": "Studio Jury on Friday 17 October, 09:00.",
            "scope": "personal",
            "origin": "reflection",
            "created_at": "2026-10-13T09:00:00",
            "expires_at": "2026-10-17T10:00:00Z",
            "speaker_id": "emma",
            "speaker_name": "Emma",
            "is_mine": true,
            "agent_name": "Researcher",
            "agent_avatar": "📚",
            "can_remove": true
        }],
        "household": []
    }"""

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(content, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun dataSource(engine: MockEngine) =
        KtorMemoryRemoteDataSource(
            client = HttpClient(engine) { install(ContentNegotiation) { json(json) } },
            baseUrl = DEFAULT_BASE_URL,
        )

    private fun unreachableHub() = MockEngine { throw IOException("Connection refused") }

    @Test
    fun `GIVEN the hub WHEN the memory page is asked for THEN both tabs come back from its page address`() =
        runTest {
            var path: String? = null
            var method: HttpMethod? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    method = request.method
                    respondJson(pageJson)
                }

            val page = dataSource(engine).getMemoryPage()

            assertEquals("/api/v1/memories/page", path)
            assertEquals(HttpMethod.Get, method)
            assertEquals("mem-1", page.yours.single().id)
            assertEquals("📚", page.yours.single().agent_avatar)
            assertEquals("2026-10-17T10:00:00Z", page.yours.single().expires_at)
            assertEquals(emptyList(), page.household)
        }

    @Test
    fun `GIVEN a memory WHEN it is removed THEN its own address is asked to delete it`() =
        runTest {
            var path: String? = null
            var method: HttpMethod? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    method = request.method
                    respond("", HttpStatusCode.NoContent)
                }

            dataSource(engine).removeMemory("mem-1")

            assertEquals("/api/v1/memories/mem-1", path)
            assertEquals(HttpMethod.Delete, method)
        }

    @Test
    fun `GIVEN a removed memory WHEN it is restored THEN its restore address is posted to`() =
        runTest {
            var path: String? = null
            var method: HttpMethod? = null
            var hadBody: String? = "unset"
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    method = request.method
                    hadBody = null
                    respondJson("""{"id": "mem-1", "user_id": "emma", "content": "x", "is_active": true}""")
                }

            dataSource(engine).restoreMemory("mem-1")

            assertEquals("/api/v1/memories/mem-1/restore", path)
            assertEquals(HttpMethod.Post, method)
            assertNull(hadBody)
        }

    private val cardJson = """{
        "id": "p-sam",
        "content": "Sam started eating fish again.",
        "stored": ${pageItem("sam", "Sam is vegetarian and doesn't eat fish.")},
        "can_answer": true,
        "answerer_name": "Emma",
        "source": {"title": "Dinner ideas", "owner_name": "Emma", "read_at": "2026-10-16T10:42:00Z"}
    }"""

    private fun pageItem(
        id: String,
        content: String,
    ) = """{
        "id": "$id", "content": "$content", "scope": "household", "origin": "reflection",
        "created_at": "2026-03-01T09:00:00Z", "speaker_id": "emma", "speaker_name": "Emma",
        "is_mine": true, "agent_name": "Planner", "agent_avatar": "🗓️", "can_remove": true
    }"""

    @Test
    fun `GIVEN the hub WHEN changes to check are asked for THEN both lists come back from the proposals address`() =
        runTest {
            var path: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respondJson("""{"to_check": [$cardJson], "waiting": []}""")
                }

            val changes = dataSource(engine).getChangesToCheck()

            assertEquals("/api/v1/memories/proposals", path)
            val card = changes.to_check.single()
            assertEquals("p-sam", card.id)
            assertEquals("sam", card.stored.id)
            assertEquals("Dinner ideas", card.source.title)
            assertEquals(emptyList(), changes.waiting)
        }

    @Test
    fun `GIVEN a change WHEN it is answered THEN the answer is posted to its answer address`() =
        runTest {
            var path: String? = null
            var method: HttpMethod? = null
            var body: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    method = request.method
                    body = (request.body as TextContent).text
                    respondJson("""{"id": "p-sam", "memory_id": "sam", "content": "x", "state": "replaced"}""")
                }

            dataSource(engine).answerChange("p-sam", "use_new")

            assertEquals("/api/v1/memories/proposals/p-sam/answer", path)
            assertEquals(HttpMethod.Post, method)
            assertEquals("""{"answer":"use_new"}""", body)
        }

    @Test
    fun `GIVEN an answered change WHEN Undo is tapped THEN its undo address is posted to`() =
        runTest {
            var path: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respondJson("""{"id": "p-sam", "memory_id": "sam", "content": "x", "state": "pending"}""")
                }

            dataSource(engine).undoChangeAnswer("p-sam")

            assertEquals("/api/v1/memories/proposals/p-sam/undo", path)
        }

    @Test
    fun `GIVEN a looked-over chat WHEN its sheet is asked for THEN it comes from the chat's looked-over address`() =
        runTest {
            var path: String? = null
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respondJson(
                        """{"at": "2026-10-16T10:52:00Z", "to_check": [$cardJson], "waiting": [],
                            "remembered": [${pageItem("dairy", "Is off dairy this month.")}]}""",
                    )
                }

            val sheet = dataSource(engine).getLookedOver("chat-1")

            assertEquals("/api/v1/sessions/chat-1/looked-over", path)
            assertEquals("2026-10-16T10:52:00Z", sheet.at)
            assertEquals("dairy", sheet.remembered.single().id)
        }

    @Test
    fun `GIVEN the hub cannot be reached WHEN any memory call is made THEN it is reported as offline`() =
        runTest {
            val source = dataSource(unreachableHub())

            assertFailsWith<ServerOfflineException> { source.getMemoryPage() }
            assertFailsWith<ServerOfflineException> { source.removeMemory("mem-1") }
            assertFailsWith<ServerOfflineException> { source.restoreMemory("mem-1") }
            assertFailsWith<ServerOfflineException> { source.getChangesToCheck() }
            assertFailsWith<ServerOfflineException> { source.answerChange("p-sam", "keep_stored") }
            assertFailsWith<ServerOfflineException> { source.undoChangeAnswer("p-sam") }
            assertFailsWith<ServerOfflineException> { source.getLookedOver("chat-1") }
        }
}
