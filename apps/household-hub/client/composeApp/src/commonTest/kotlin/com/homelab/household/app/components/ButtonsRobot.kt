package com.homelab.household.app.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.width
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_working
import com.homelab.household.app.theme.DefaultSizes
import org.jetbrains.compose.resources.getString
import kotlin.test.assertTrue

/** What a person sees of a button: its label, whether it takes a tap, how wide it is and whether it is working. */
@OptIn(ExperimentalTestApi::class)
class ButtonsRobot(
    private val test: ComposeUiTest,
) {
    fun tapsTheButton(label: String) {
        button(label).performClick()
    }

    /** Never disabled, a real button, and big enough to hit. */
    fun seesItEnabledAsAButtonBigEnoughToHit(label: String) {
        button(label)
            .assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(DefaultSizes.touchTarget)
    }

    /** Working is announced as "working", and the button is not dimmed while it is. */
    suspend fun seesItEnabledAndSayingItIsWorking(label: String) {
        button(label)
            .assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_working),
                ),
            )
    }

    /** The wait is announced in the caller's own words. */
    fun seesItAnnouncing(
        label: String,
        description: String,
    ) {
        button(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description))
    }

    fun seesTheProgressBar() {
        test.waitUntilExactlyOneExists(hasTestTag(HEARTH_PROGRESS_BAR_TAG), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertIsDisplayed()
    }

    fun doesNotSeeTheProgressBar() {
        test.onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertDoesNotExist()
    }

    fun seesItAsWideAs(
        label: String,
        width: Dp,
    ) {
        button(label).assertWidthIsEqualTo(width)
    }

    fun seesItNarrowerThan(
        label: String,
        limit: Dp,
    ) {
        val width = button(label).getUnclippedBoundsInRoot().width
        assertTrue(width < limit, "a button given no width should wrap its label, but measured $width")
    }

    private fun button(label: String): SemanticsNodeInteraction {
        test.waitUntilExactlyOneExists(hasText(label), timeoutMillis = WAIT_MILLIS)
        return test.onNodeWithText(label)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onButtons(block: suspend ButtonsRobot.() -> Unit) {
    ButtonsRobot(this).block()
}
