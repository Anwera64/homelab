package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.wait_slow_hub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.util.WaitPhase
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

/**
 * The third beat of a wait: at eight seconds a quiet line joins whatever pattern is already on
 * show. It is not an error — nothing has failed, and the bar keeps running — so it is drawn in the
 * muted role every other aside uses, never the error one.
 */
@OptIn(ExperimentalTestApi::class)
class SlowLineTest {
    @Test
    fun a_hub_taking_longer_than_usual_says_so_quietly() =
        runComposeUiTest {
            setContent { StillTheme { SlowLine(WaitPhase.Slow) } }

            onNodeWithText(getString(Res.string.wait_slow_hub)).assertIsDisplayed()
        }

    /**
     * And says nothing at all before then. A wait that is going normally has no news, and a caption
     * that appeared at 250ms would make every call look like a problem.
     */
    @Test
    fun a_wait_going_normally_has_nothing_to_say() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    SlowLine(WaitPhase.Hidden)
                    SlowLine(WaitPhase.Showing)
                }
            }

            onNodeWithText(getString(Res.string.wait_slow_hub)).assertDoesNotExist()
        }
}
