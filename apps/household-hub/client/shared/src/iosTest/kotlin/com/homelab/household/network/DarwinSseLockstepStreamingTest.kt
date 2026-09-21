package com.homelab.household.network

import com.homelab.household.data.network.HubConfig
import com.homelab.household.domain.model.ChatStreamEvent
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.network.SseFixture.ack
import com.homelab.household.sdk.HouseholdHubSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Proves the Darwin (NSURLSession) engine delivers SSE deltas **incrementally**, through the
 * real production object graph: the Koin `platformModule` Darwin engine, the real `HttpClient`,
 * the real `SessionRepositoryImpl.streamChatTurn`, and the real `DefensiveSseStreamReader` —
 * collected as a `Flow<ChatStreamEvent>` from a dispatcher that is not the engine's, which is
 * the boundary where a real defect once dropped every event on iOS.
 *
 * Why this is evidence and not a timing coincidence: the fixture will not write delta N+1 until
 * the client has told it, over a **separate** connection, that delta N arrived. The server
 * therefore *cannot physically* have put the later bytes on the wire before the client saw the
 * earlier ones. An engine that buffered the response body would never send an ack, the fixture
 * would time out waiting, and the assertion below would fail on content — it cannot pass by
 * luck. The wall-clock assertion is a second, independent line of defence: a buffered body
 * would surface all three deltas in the same instant, whereas here each pair is separated by
 * the gap the fixture sleeps after each ack.
 *
 * Fast (~2s of fixture-imposed delay); runs on every `iosSimulatorArm64Test`.
 */
class DarwinSseLockstepStreamingTest : KoinTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun darwin_delivers_each_sse_delta_before_the_server_is_allowed_to_write_the_next() = runBlocking {
        HouseholdHubSdk.init(HubConfig(SseFixture.BASE_URL))
        val repository = get<SessionRepository>()
        val acks = SseFixture.ackClient()

        val events = mutableListOf<ChatStreamEvent>()
        val deltaArrivals = mutableListOf<Duration>()
        val clock = TimeSource.Monotonic.markNow()

        try {
            withTimeout(90.seconds) {
                // The engine hands bytes over on NSURLSession's own queue; collecting on
                // Dispatchers.Default forces every event across a dispatcher boundary, exactly
                // as the app does. `streamChatTurn` is a channelFlow for this reason.
                withContext(Dispatchers.Default) {
                    repository.streamChatTurn(
                        sessionId = SseFixture.SCENARIO_LOCKSTEP,
                        content = "prove incremental delivery"
                    ).collect { event ->
                        events += event
                        if (event is ChatStreamEvent.Delta) {
                            deltaArrivals += clock.elapsedNow()
                            acks.ack(SseFixture.SCENARIO_LOCKSTEP, deltaArrivals.size)
                        }
                    }
                }
            }
        } finally {
            acks.close()
        }

        assertEquals(
            listOf("alpha ", "beta ", "gamma"),
            events.filterIsInstance<ChatStreamEvent.Delta>().map { it.content },
            "Deltas did not arrive intact; got $events"
        )
        assertEquals(4, events.size, "Expected three deltas and one done event, got $events")

        val done = events.last()
        assertTrue(done is ChatStreamEvent.Done, "Stream did not end with a Done event: $done")
        assertEquals("alpha beta gamma", done.assistantContent)
        assertEquals("msg-lockstep", done.messageId)

        // Independent of the handshake: a buffered body would deliver these microseconds apart.
        val floor = (SseFixture.GAP_MILLIS - 150).milliseconds
        for (index in 1 until deltaArrivals.size) {
            val gap = deltaArrivals[index] - deltaArrivals[index - 1]
            assertTrue(
                gap >= floor,
                "Delta ${index + 1} arrived only $gap after delta $index; the fixture sleeps " +
                    "${SseFixture.GAP_MILLIS}ms between them, so anything under $floor means the " +
                    "engine had already buffered them. Arrivals: $deltaArrivals"
            )
        }
    }
}
