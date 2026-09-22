package com.homelab.household.app.util

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * With motion off there is nothing to wait for, so the phase is right from the very first frame —
 * which is the only frame a static preview ever draws.
 */
@OptIn(ExperimentalTestApi::class)
class RememberWaitPhaseTest {
    @Test
    fun `GIVEN motion is off WHEN a wait begins THEN the first frame already shows it`() =
        runComposeUiTest {
            assertEquals(WaitPhase.Showing, firstFrameOf(busy = true))
        }

    @Test
    fun `GIVEN motion is off WHEN nothing is being waited on THEN the first frame shows nothing`() =
        runComposeUiTest {
            assertEquals(WaitPhase.Hidden, firstFrameOf(busy = false))
        }

    private fun androidx.compose.ui.test.ComposeUiTest.firstFrameOf(busy: Boolean): WaitPhase? {
        var first: WaitPhase? = null
        setContent {
            StillTheme {
                val phase = rememberWaitPhase(busy)
                if (first == null) first = phase
            }
        }
        return first
    }
}
