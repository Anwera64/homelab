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
    fun `GIVEN a hold of 250 milliseconds WHEN less than the hold has passed THEN the phase is hidden`() {
        assertEquals(WaitPhase.Hidden, waitPhase(elapsed = 0.milliseconds, hold = hold, slow = slow))
        assertEquals(WaitPhase.Hidden, waitPhase(elapsed = 249.milliseconds, hold = hold, slow = slow))
    }

    @Test
    fun `GIVEN a hold of 250 milliseconds WHEN the hold has been reached THEN the phase is showing`() {
        assertEquals(WaitPhase.Showing, waitPhase(elapsed = 250.milliseconds, hold = hold, slow = slow))
        assertEquals(WaitPhase.Showing, waitPhase(elapsed = 251.milliseconds, hold = hold, slow = slow))
    }

    @Test
    fun `GIVEN a slow threshold of eight seconds WHEN less than the threshold has passed THEN the phase is still showing`() {
        assertEquals(WaitPhase.Showing, waitPhase(elapsed = 4.seconds, hold = hold, slow = slow))
        assertEquals(WaitPhase.Showing, waitPhase(elapsed = 7999.milliseconds, hold = hold, slow = slow))
    }

    @Test
    fun `GIVEN a slow threshold of eight seconds WHEN the threshold has been reached THEN the phase is slow`() {
        assertEquals(WaitPhase.Slow, waitPhase(elapsed = 8.seconds, hold = hold, slow = slow))
        assertEquals(WaitPhase.Slow, waitPhase(elapsed = 30.seconds, hold = hold, slow = slow))
    }

    @Test
    fun `GIVEN a hold of zero WHEN no time has passed THEN the phase is showing`() {
        // What StillMotion does in tests: nothing is time-gated, so a screen test never waits.
        val phase = waitPhase(elapsed = 0.milliseconds, hold = 0.milliseconds, slow = slow)
        assertEquals(WaitPhase.Showing, phase)
    }
}
