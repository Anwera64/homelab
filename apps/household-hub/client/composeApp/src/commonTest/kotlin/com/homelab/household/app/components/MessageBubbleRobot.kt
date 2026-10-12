package com.homelab.household.app.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Dp
import kotlin.test.assertEquals

/** A turn as it lands on screen: the prose, its bullets, its gaps and its links. */
@OptIn(ExperimentalTestApi::class)
class MessageBubbleRobot(
    private val test: ComposeUiTest,
) {
    fun seesText(text: String) {
        line(text).assertIsDisplayed()
    }

    fun doesNotSeeAnyTextContaining(fragment: String) {
        test.onAllNodes(hasText(fragment, substring = true)).assertCountEquals(0)
    }

    /** Each list item is drawn behind a bullet glyph of its own. */
    fun seesBullets(count: Int) {
        test.onAllNodesWithText(BULLET).assertCountEquals(count)
    }

    fun seesGapBetween(
        upper: String,
        lower: String,
        gap: Dp,
    ) {
        val upperBounds = line(upper).getUnclippedBoundsInRoot()
        val lowerBounds = line(lower).getUnclippedBoundsInRoot()
        assertEquals(gap, lowerBounds.top - upperBounds.bottom)
    }

    fun tapsText(text: String) {
        line(text).performClick()
    }

    private fun line(text: String): SemanticsNodeInteraction {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        return test.onNodeWithText(text)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L

        /** The glyph the bubble draws before a list item; it is drawn by the component, not read from a resource. */
        const val BULLET = "•"
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onMessageBubble(block: suspend MessageBubbleRobot.() -> Unit) {
    MessageBubbleRobot(this).block()
}
