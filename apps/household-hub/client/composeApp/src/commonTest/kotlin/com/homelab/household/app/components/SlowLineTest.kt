package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.util.WaitPhase
import kotlin.test.Test

/**
 * The third beat of a wait: at eight seconds a quiet line joins whatever pattern is already on
 * show. It is not an error — nothing has failed, and the bar keeps running — so it is drawn in the
 * muted role every other aside uses, never the error one.
 */
@OptIn(ExperimentalTestApi::class)
class SlowLineTest {
    @Test
    fun `GIVEN a slow line in the slow phase WHEN it is shown THEN it says the hub is slow`() =
        runComposeUiTest {
            setContent { StillTheme { SlowLine(WaitPhase.Slow) } }

            onSlowLine { seesTheHubIsSlow() }
        }

    /**
     * And says nothing at all before then. A wait that is going normally has no news, and a caption
     * that appeared at 250ms would make every call look like a problem.
     */
    @Test
    fun `GIVEN slow lines in the hidden and showing phases WHEN they are shown THEN neither says the hub is slow`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    SlowLine(WaitPhase.Hidden)
                    SlowLine(WaitPhase.Showing)
                }
            }

            onSlowLine { doesNotSeeTheHubIsSlow() }
        }
}
