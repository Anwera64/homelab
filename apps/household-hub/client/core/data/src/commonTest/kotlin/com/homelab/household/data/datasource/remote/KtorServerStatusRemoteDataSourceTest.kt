package com.homelab.household.data.datasource.remote

import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.domain.model.ServerStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one data source that answers with a domain model rather than a DTO. `checkHealth` folds a
 * failure into [ServerStatus.Offline] instead of throwing, because "is the hub there?" has no
 * failure case — an unreachable hub *is* the answer, and it is what the launch screen reads.
 */
class KtorServerStatusRemoteDataSourceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun dataSource(engine: MockEngine) = KtorServerStatusRemoteDataSource(
        client = HttpClient(engine) { install(ContentNegotiation) { json(json) } },
        baseUrl = DEFAULT_BASE_URL,
    )

    private fun hubAnswering(body: String, status: HttpStatusCode = HttpStatusCode.OK, contentType: String = "application/json") =
        MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) }

    // ---- checkHealth -------------------------------------------------------

    @Test
    fun `GIVEN a healthy hub WHEN its health is checked THEN it reports online`() = runTest {
        // GIVEN
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respond(
                """{"status": "ok", "version": "0.1.0", "database": "connected"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        // WHEN
        val status = dataSource(engine).checkHealth()

        // THEN
        assertEquals("/api/v1/health", path)
        assertTrue(status is ServerStatus.Online)
    }

    @Test
    fun `GIVEN a hub that cannot be reached WHEN its health is checked THEN it reports offline rather than throwing`() = runTest {
        // GIVEN
        val engine = MockEngine { throw IOException("Connection refused") }

        // WHEN
        val status = dataSource(engine).checkHealth()

        // THEN
        assertTrue(status is ServerStatus.Offline)
        assertTrue(status.reason.isNotEmpty())
    }

    @Test
    fun `GIVEN a hub that says it is degraded WHEN its health is checked THEN it reports offline and says why`() = runTest {
        // GIVEN
        val engine = hubAnswering("""{"status": "degraded", "database": "disconnected"}""")

        // WHEN
        val status = dataSource(engine).checkHealth()

        // THEN
        assertTrue(status is ServerStatus.Offline)
        assertTrue(status.reason.contains("degraded"))
    }

    @Test
    fun `GIVEN a captive portal answering with HTML WHEN the hub's health is checked THEN it reports offline`() = runTest {
        // GIVEN
        val engine = hubAnswering("<html>Captive Portal Login</html>", contentType = "text/html")

        // WHEN
        val status = dataSource(engine).checkHealth()

        // THEN
        assertTrue(status is ServerStatus.Offline)
    }

    @Test
    fun `GIVEN a hub answering with an error status WHEN its health is checked THEN it reports offline with the code`() = runTest {
        // GIVEN
        val engine = hubAnswering("""{"detail": "nope"}""", status = HttpStatusCode.ServiceUnavailable)

        // WHEN
        val status = dataSource(engine).checkHealth()

        // THEN
        assertTrue(status is ServerStatus.Offline)
        assertTrue(status.reason.contains("503"))
    }

    // ---- observeStatus -----------------------------------------------------

    @Test
    fun `GIVEN a healthy hub WHEN its status is watched THEN it is checked again at a steady interval`() = runTest {
        // GIVEN
        val engine = hubAnswering("""{"status": "ok"}""")
        val checkedAt = mutableListOf<Long>()

        // WHEN
        dataSource(engine).observeStatus(intervalSeconds = 10).take(3).collect { checkedAt += testScheduler.currentTime }

        // THEN
        assertEquals(listOf(0L, 10_000L, 20_000L), checkedAt)
    }

    /**
     * The backoff is only testable at all because the wait is on `runTest`'s virtual clock: three
     * checks of a hub that is down would otherwise take 37 real seconds.
     */
    @Test
    fun `GIVEN a hub that stays down WHEN its status is watched THEN the wait before each retry grows`() = runTest {
        // GIVEN
        val engine = MockEngine { throw IOException("Connection refused") }
        val checkedAt = mutableListOf<Long>()

        // WHEN
        dataSource(engine).observeStatus(intervalSeconds = 10).take(3).collect { checkedAt += testScheduler.currentTime }

        // THEN — 15s after the first failure, then half as long again.
        assertEquals(listOf(0L, 15_000L, 37_500L), checkedAt)
    }
}
