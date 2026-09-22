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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * ### SLOW — costs more than [SseFixture.PAUSE_SECONDS] seconds of wall clock, by design.
 *
 * Run it with:
 *
 *     ./gradlew :shared:iosSimulatorArm64SlowSseLongPauseTest
 *
 * It is excluded from the ordinary `iosSimulatorArm64Test` so nobody pays 75+ seconds on a
 * normal run. There is no way to make it cheap: the whole point is to outlast a 60-second
 * timeout, so the test must actually wait longer than 60 seconds.
 *
 * ### What it proves
 *
 * NSURLSession's **default** `timeoutIntervalForRequest` is 60 seconds, and it measures the gap
 * between bytes, not the total duration — so a long enough silence mid-answer kills the stream,
 * exactly as OkHttp's read timeout did on Android. `PlatformModule.ios.kt` raises it to 3600s.
 * Nothing else in the suite notices if that line is deleted.
 *
 * The fixture writes one delta, waits until the client acks it (so the connection is provably
 * live and mid-body), goes silent for [SseFixture.PAUSE_SECONDS], then writes a second delta.
 * With the production setting the stream survives and both deltas arrive; without it
 * NSURLSession fails the task with `NSURLErrorTimedOut` and the flow throws instead. Note that a
 * 15-second pause — what the Android sibling test uses, calibrated to OkHttp's 10s default —
 * proves nothing here: it fits comfortably inside NSURLSession's default and would pass with the
 * configuration removed entirely.
 */
class DarwinSseLongPauseTest : KoinTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun darwin_stream_survives_a_pause_longer_than_nsurlsessions_default_request_timeout() =
        runBlocking {
            HouseholdHubSdk.init(HubConfig(SseFixture.BASE_URL))
            val repository = get<SessionRepository>()
            val acks = SseFixture.ackClient()

            val events = mutableListOf<ChatStreamEvent>()
            val deltaArrivals = mutableListOf<Duration>()
            val clock = TimeSource.Monotonic.markNow()

            try {
                withTimeout(5.minutes) {
                    withContext(Dispatchers.Default) {
                        repository
                            .streamChatTurn(
                                sessionId = SseFixture.SCENARIO_LONG_PAUSE,
                                content = "prove the stream survives a long silence",
                            ).collect { event ->
                                events += event
                                if (event is ChatStreamEvent.Delta) {
                                    deltaArrivals += clock.elapsedNow()
                                    acks.ack(SseFixture.SCENARIO_LONG_PAUSE, deltaArrivals.size)
                                }
                            }
                    }
                }
            } finally {
                acks.close()
            }

            assertEquals(
                listOf("before-pause ", "after-pause"),
                events.filterIsInstance<ChatStreamEvent.Delta>().map { it.content },
                "The stream did not survive the pause; got $events",
            )

            val done = events.last()
            assertTrue(done is ChatStreamEvent.Done, "Stream did not end with a Done event: $done")
            assertEquals("before-pause after-pause", done.assistantContent)

            // Guards the test against its own fixture: if the pause were ever shortened below
            // NSURLSession's 60s default, this test would silently stop proving anything.
            val gap = deltaArrivals[1] - deltaArrivals[0]
            assertTrue(
                gap > 60.seconds,
                "The silence between deltas was only $gap. NSURLSession's default " +
                    "timeoutIntervalForRequest is 60s, so a shorter gap proves nothing about the " +
                    "3600s setting in PlatformModule.ios.kt.",
            )
        }
}
