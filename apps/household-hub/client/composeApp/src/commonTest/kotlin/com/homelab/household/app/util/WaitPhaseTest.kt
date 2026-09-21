package com.homelab.household.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The waiting rule itself, not any screen's use of it. Arithmetic over two thresholds: no clock,
 * no coroutines and no Compose, so there is nothing here that can be flaky.
 *
 * The failure worth catching is an inverted comparison — answering [WaitPhase.Showing] where the
 * hold should still answer [WaitPhase.Hidden] defeats the hold entirely and makes every call on a
 * fast hub flash a loading state.
 */
class WaitPhaseTest {

    private val hold = 250.milliseconds
    private val slow = 8.seconds

    @Test
    fun nothing_is_drawn_before_the_hold_has_passed() {
        assertEquals(WaitPhase.Hidden, waitPhase(elapsed = 0.milliseconds, hold = hold, slow = slow))
        assertEquals(WaitPhase.Hidden, waitPhase(elapsed = 249.milliseconds, hold = hold, slow = slow))
    }

    @Test
    fun the_pattern_appears_once_the_hold_is_reached() {
        assertEquals(WaitPhase.Showing, waitPhase(elapsed = 250.milliseconds, hold = hold, slow = slow))
        assertEquals(WaitPhase.Showing, waitPhase(elapsed = 251.milliseconds, hold = hold, slow = slow))
    }

    @Test
    fun the_pattern_stays_the_whole_way_to_the_slow_threshold() {
        assertEquals(WaitPhase.Showing, waitPhase(elapsed = 4.seconds, hold = hold, slow = slow))
        assertEquals(WaitPhase.Showing, waitPhase(elapsed = 7999.milliseconds, hold = hold, slow = slow))
    }

    @Test
    fun a_hub_taking_longer_than_usual_reaches_the_slow_phase() {
        assertEquals(WaitPhase.Slow, waitPhase(elapsed = 8.seconds, hold = hold, slow = slow))
        assertEquals(WaitPhase.Slow, waitPhase(elapsed = 30.seconds, hold = hold, slow = slow))
    }

    @Test
    fun a_scale_that_collapses_the_hold_shows_the_pattern_at_once() {
        // What StillMotion does in tests: nothing is time-gated, so a screen test never waits.
        val phase = waitPhase(elapsed = 0.milliseconds, hold = 0.milliseconds, slow = slow)
        assertEquals(WaitPhase.Showing, phase)
    }
}
