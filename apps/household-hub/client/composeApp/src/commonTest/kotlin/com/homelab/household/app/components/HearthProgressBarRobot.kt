package com.homelab.household.app.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_working
import org.jetbrains.compose.resources.getString

/** The one progress bar the app has, as a person (or a screen reader) meets it. */
@OptIn(ExperimentalTestApi::class)
class HearthProgressBarRobot(
    private val test: ComposeUiTest,
) {
    /** The bar is on screen and announces that something is working. */
    suspend fun seesTheBarWorking() {
        test.waitUntilExactlyOneExists(hasTestTag(HEARTH_PROGRESS_BAR_TAG), timeoutMillis = WAIT_MILLIS)
        test
            .onNodeWithTag(HEARTH_PROGRESS_BAR_TAG)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_working),
                ),
            )
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onHearthProgressBar(block: suspend HearthProgressBarRobot.() -> Unit) {
    HearthProgressBarRobot(this).block()
}
