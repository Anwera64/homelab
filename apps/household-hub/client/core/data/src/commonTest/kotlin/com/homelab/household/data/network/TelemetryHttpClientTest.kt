package com.homelab.household.data.network

import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.data.telemetry.telemetryHeaders
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The client logs are sent with is not the one the app talks to the hub with, and the difference
 * that matters is what a 401 does: on the hub's client it signs the member out.
 */
class TelemetryHttpClientTest {
    @Test
    fun `GIVEN a signed-in phone WHEN the log endpoint answers 401 THEN nobody is signed out`() =
        runTest {
            // GIVEN
            val storage = InMemorySessionStorage().apply { saveTokens("jwt-token-123") }
            val client =
                telemetryHttpClient(MockEngine { respond("", HttpStatusCode.Unauthorized) }) {
                    telemetryHeaders(storage)
                }

            // WHEN
            val response = client.post("https://telemetry.test/v1/logs") { setBody(ByteArray(4)) }

            // THEN
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("jwt-token-123", storage.getAccessToken())
        }

    @Test
    fun `GIVEN the token changes between two posts WHEN each is sent THEN it carries the token kept at that moment`() =
        runTest {
            // GIVEN
            val seen = mutableListOf<String?>()
            val storage = InMemorySessionStorage().apply { saveTokens("first-token") }
            val engine =
                MockEngine { request ->
                    seen += request.headers[HttpHeaders.Authorization]
                    respond("", HttpStatusCode.OK)
                }
            val client = telemetryHttpClient(engine) { telemetryHeaders(storage) }

            // WHEN
            client.post("https://telemetry.test/v1/logs") { setBody(ByteArray(4)) }
            storage.saveTokens("second-token")
            client.post("https://telemetry.test/v1/logs") { setBody(ByteArray(4)) }

            // THEN
            assertEquals(listOf<String?>("Bearer first-token", "Bearer second-token"), seen)
        }

    @Test
    fun `GIVEN nobody signed in WHEN a post is sent THEN it carries no Authorization header`() =
        runTest {
            // GIVEN
            var authorization: String? = "not asked yet"
            val engine =
                MockEngine { request ->
                    authorization = request.headers[HttpHeaders.Authorization]
                    respond("", HttpStatusCode.Unauthorized)
                }
            val client = telemetryHttpClient(engine) { telemetryHeaders(InMemorySessionStorage()) }

            // WHEN
            client.post("https://telemetry.test/v1/logs") { setBody(ByteArray(4)) }

            // THEN
            assertNull(authorization)
        }
}
