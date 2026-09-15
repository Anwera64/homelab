package com.homelab.household.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals

/**
 * Client-side half of `client/tools/sse_fixture.py`, the external SSE server the Darwin
 * streaming proofs talk to. Gradle starts and stops it around the iOS simulator test tasks
 * (see `startSseFixture` / `stopSseFixture` in `shared/build.gradle.kts`).
 */
object SseFixture {

    /**
     * Fixed, so a leaked server is noticed loudly instead of silently moving out of the way.
     * Must match the `sseFixturePort` in `shared/build.gradle.kts`.
     */
    const val PORT: Int = 8749

    const val BASE_URL: String = "http://127.0.0.1:$PORT"

    /** Session id that selects the ack-gated lock-step scenario. */
    const val SCENARIO_LOCKSTEP: String = "lockstep"

    /** Session id that selects the scenario with a pause longer than NSURLSession's default. */
    const val SCENARIO_LONG_PAUSE: String = "long-pause"

    /**
     * The fixture sleeps this long *after* each ack before writing the next delta, so the
     * client can assert a real wall-clock separation on top of the causal handshake.
     * Must match `--gap-millis` in `shared/build.gradle.kts`.
     */
    const val GAP_MILLIS: Long = 400

    /** Must match `--pause-seconds` in `shared/build.gradle.kts`. */
    const val PAUSE_SECONDS: Long = 75

    /**
     * A second HTTP client on its own NSURLSession, used only to tell the fixture that a
     * delta arrived. Deliberately *not* the client under test: a separate session cannot
     * share a connection or a queue with the stream, so the ack can never be an artefact of
     * the streaming connection's state.
     */
    fun ackClient(): HttpClient = HttpClient(Darwin.create())

    /** Confirms to the fixture that delta [index] of [scenario] reached the collector. */
    suspend fun HttpClient.ack(scenario: String, index: Int) {
        val response: HttpResponse = get("$BASE_URL/fixture/ack/$scenario/$index")
        assertEquals(
            HttpStatusCode.OK,
            response.status,
            "Could not ack $scenario delta $index; is the fixture on port $PORT running?"
        )
    }
}
