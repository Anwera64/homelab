package com.homelab.household.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [sendDeviceTimeZone] carries the phone's current IANA zone to the hub so it can date an agent's
 * prompt in the user's own day — proven here against the same configuration the app installs.
 */
class TimeZoneHeaderTest {
    @Test
    fun `GIVEN a device zone WHEN two different calls are made THEN both carry it as X-Timezone`() =
        runTest {
            // GIVEN
            val capturedHeaders = mutableListOf<String?>()
            val mockEngine =
                MockEngine { request ->
                    capturedHeaders.add(request.headers[TIME_ZONE_HEADER])
                    respond("ok", HttpStatusCode.OK)
                }
            val client =
                HttpClient(mockEngine) {
                    sendDeviceTimeZone { "America/Lima" }
                }

            // WHEN
            client.get("/api/v1/spaces/shared")
            client.get("/api/v1/agents")

            // THEN
            assertEquals(listOf<String?>("America/Lima", "America/Lima"), capturedHeaders)
        }

    @Test
    fun `GIVEN the device zone changes between calls WHEN each call is sent THEN it carries the zone current at send time`() =
        runTest {
            // GIVEN
            val capturedHeaders = mutableListOf<String?>()
            val mockEngine =
                MockEngine { request ->
                    capturedHeaders.add(request.headers[TIME_ZONE_HEADER])
                    respond("ok", HttpStatusCode.OK)
                }
            var zone = "America/Lima"
            val client =
                HttpClient(mockEngine) {
                    sendDeviceTimeZone { zone }
                }

            // WHEN
            client.get("/api/v1/spaces/shared")
            zone = "Asia/Tokyo"
            client.get("/api/v1/agents")

            // THEN
            assertEquals(listOf<String?>("America/Lima", "Asia/Tokyo"), capturedHeaders)
        }
}
