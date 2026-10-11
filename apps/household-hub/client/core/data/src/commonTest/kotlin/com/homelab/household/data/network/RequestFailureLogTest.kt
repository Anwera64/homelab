package com.homelab.household.data.network

import co.touchlab.kermit.Severity
import com.homelab.household.data.telemetry.RecordingLogWriter
import com.homelab.household.domain.util.runCatchingSafe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [logFailedRequests] is the one place a hub call turns into a line that leaves the phone, so these
 * are as much about what the line leaves out as about what it says.
 */
class RequestFailureLogTest {
    private class LineDown(
        message: String,
    ) : RuntimeException(message)

    private fun client(
        writer: RecordingLogWriter,
        engine: MockEngine,
    ) = HttpClient(engine) {
        logFailedRequests(writer.logger(REQUEST_LOG_TAG))
    }

    @Test
    fun `GIVEN a hub answering 503 WHEN a conversation is asked for THEN the line says the method the templated path and the status`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val client = client(writer, MockEngine { respond("down for a nap", HttpStatusCode.ServiceUnavailable) })

            // WHEN
            client.get("https://hub.test/api/v1/sessions/3f2a9c1e-7b64-4d0a-9e51-0c8d2b6f4a17")

            // THEN
            val line = writer.lines.single()
            assertEquals(Severity.Warn, line.severity)
            assertEquals("Request failed: GET /api/v1/sessions/{id} -> 503", line.message)
            assertNull(line.throwable)
        }

    @Test
    fun `GIVEN a hub refusing a call WHEN it carried a query a header and a body THEN none of them is in the line`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val client = client(writer, MockEngine { respond("the hub's reasons", HttpStatusCode.BadRequest) })

            // WHEN
            client.post(
                "https://hub.test/api/v1/sessions/3f2a9c1e-7b64-4d0a-9e51-0c8d2b6f4a17/chat/stream?search=dentist",
            ) {
                header("Authorization", "Bearer jwt-token-123")
                setBody("when is the dentist appointment")
            }

            // THEN
            val line = writer.lines.single()
            assertEquals("Request failed: POST /api/v1/sessions/{id}/chat/stream -> 400", line.message)
            listOf("dentist", "search", "jwt-token-123", "Bearer", "Authorization", "reasons", "hub.test").forEach {
                assertFalse(it in line.message, "'$it' leaked into: ${line.message}")
            }
        }

    @Test
    fun `GIVEN a hub answering 200 WHEN a call is made THEN nothing is logged`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val client = client(writer, MockEngine { respond("{}", HttpStatusCode.OK) })

            // WHEN
            client.get("https://hub.test/api/v1/agents")

            // THEN
            assertTrue(writer.lines.isEmpty())
        }

    @Test
    fun `GIVEN a hub that cannot be reached WHEN a call is made THEN the line names the kind of failure and not what it said`() =
        runTest {
            // GIVEN
            val writer = RecordingLogWriter()
            val client = client(writer, MockEngine { throw LineDown("no route to hub.test at 192.168.1.20") })

            // WHEN
            val outcome = runCatchingSafe { client.get("https://hub.test/api/v1/agents?search=dentist") }

            // THEN
            assertTrue(outcome.exceptionOrNull() is LineDown, "the failure still reaches the caller")
            val line = writer.lines.single()
            assertEquals(Severity.Warn, line.severity)
            assertEquals("Request failed: GET /api/v1/agents -> LineDown", line.message)
            assertNull(line.throwable)
        }

    @Test
    fun `GIVEN the tag failed calls are logged under WHEN it is compared with Ktor's own THEN they differ`() {
        // GIVEN
        val ktorTag = HTTP_CLIENT_LOG_TAG

        // WHEN
        val failureTag = REQUEST_LOG_TAG

        // THEN
        assertFalse(failureTag == ktorTag, "lines under Ktor's tag are never exported")
    }
}
