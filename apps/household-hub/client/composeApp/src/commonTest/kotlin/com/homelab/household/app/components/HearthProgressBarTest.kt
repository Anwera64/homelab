package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_working
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.LocalHearthMotion
import com.homelab.household.app.theme.StillMotion
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

/**
 * The one bar the app has: Launch's, the picker's, the one under a working button and the one that
 * runs under a header on a refresh are all this component at different widths.
 *
 * Every case runs under [StillMotion]. The bar's own animation is indeterminate and endless, and an
 * endless animation never lets the composition go idle — a test that forgets this hangs rather than
 * failing. What is asserted is the resting render, never the motion.
 */
@OptIn(ExperimentalTestApi::class)
class HearthProgressBarTest {

    @Test
    fun the_track_bar_rests_where_a_test_can_see_it() = restingBar {
        HearthProgressBar()
    }

    @Test
    fun the_full_bleed_bar_rests_where_a_test_can_see_it() = restingBar {
        HearthProgressBar(width = HearthProgressBarWidth.FullBleed)
    }

    @Test
    fun the_inset_bar_rests_where_a_test_can_see_it() = restingBar {
        HearthProgressBar(width = HearthProgressBarWidth.Inset)
    }

    private fun restingBar(bar: @Composable () -> Unit) = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHearthMotion provides StillMotion) { bar() }
            }
        }

        onNodeWithTag(HearthProgressBarTag)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_working)
                )
            )
    }
}
