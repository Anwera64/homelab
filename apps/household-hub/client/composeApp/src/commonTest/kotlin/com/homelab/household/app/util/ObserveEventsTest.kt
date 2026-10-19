package com.homelab.household.app.util

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import com.homelab.household.app.testing.runScreenTest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/** One-shot events reach the screen once each, and never twice on recomposition. */
@OptIn(ExperimentalTestApi::class)
class ObserveEventsTest {
    @Test
    fun `GIVEN a screen observing events WHEN an event is sent THEN the collector receives it`() =
        runScreenTest {
            val events = Channel<String>(Channel.BUFFERED)
            val seen = mutableListOf<String>()

            setContent {
                ObserveEvents(events.receiveAsFlow()) { seen += it }
                Text("screen")
            }

            events.trySend("go")
            waitForIdle()

            assertEquals(listOf("go"), seen)
        }

    @Test
    fun `GIVEN a screen that already received an event WHEN another event is sent THEN each event is received once`() =
        runScreenTest {
            val events = Channel<String>(Channel.BUFFERED)
            val seen = mutableListOf<String>()

            setContent {
                ObserveEvents(events.receiveAsFlow()) { seen += it }
                Text("screen")
            }

            events.trySend("go")
            waitForIdle()
            events.trySend("again")
            waitForIdle()

            assertEquals(listOf("go", "again"), seen)
        }
}
