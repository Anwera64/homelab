package com.homelab.household.app.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Dp
import kotlin.test.assertEquals

/** A scaffolded screen: pinned bars, and a content region that moves under them. */
@OptIn(ExperimentalTestApi::class)
class HearthScaffoldRobot(
    private val test: ComposeUiTest,
) {
    fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    fun scrollsToAndSees(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    fun doesNotSeeOnScreen(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsNotDisplayed()
    }

    fun scrollsTheListTo(
        listTag: String,
        index: Int,
    ) {
        test.waitUntilExactlyOneExists(hasTestTag(listTag), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithTag(listTag).performScrollToIndex(index)
    }

    /** The content starts [top] from the top of the window and ends [bottom] above its bottom edge. */
    fun seesContentBetween(
        contentTag: String,
        top: Dp,
        bottom: Dp,
    ) {
        test.waitUntilExactlyOneExists(hasTestTag(contentTag), timeoutMillis = WAIT_MILLIS)
        val root = test.onRoot().getBoundsInRoot()
        val content = test.onNodeWithTag(contentTag).getBoundsInRoot()

        assertEquals(top, content.top)
        assertEquals(root.bottom - bottom, content.bottom)
    }

    fun seesContentAt(
        contentTag: String,
        top: Dp,
        left: Dp,
    ) {
        test.waitUntilExactlyOneExists(hasTestTag(contentTag), timeoutMillis = WAIT_MILLIS)
        test
            .onNodeWithTag(contentTag)
            .assertTopPositionInRootIsEqualTo(top)
            .assertLeftPositionInRootIsEqualTo(left)
    }

    fun focusesTheField(fieldTag: String) {
        test.waitUntilExactlyOneExists(hasTestTag(fieldTag), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithTag(fieldTag).performClick().assertIsFocused()
    }

    /** A tap on a spot that holds no control, as a finger beside the field would land. */
    fun tapsEmptySpace(emptyTag: String) {
        test.waitUntilExactlyOneExists(hasTestTag(emptyTag), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithTag(emptyTag).performClick()
    }

    fun tapsTheButton(label: String) {
        test.waitUntilExactlyOneExists(hasText(label), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(label).performClick()
    }

    /** A finger scrolls the form, as it would to reach a field further down. */
    fun dragsUp(formTag: String) {
        test.waitUntilExactlyOneExists(hasTestTag(formTag), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithTag(formTag).performTouchInput { swipeUp() }
    }

    /** A finger moves along the field's line, as it does to reach the end of a long title. */
    fun dragsAlong(fieldTag: String) {
        test.waitUntilExactlyOneExists(hasTestTag(fieldTag), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithTag(fieldTag).performTouchInput { swipeLeft() }
    }

    fun seesTheFieldFocused(fieldTag: String) {
        test.onNodeWithTag(fieldTag).assertIsFocused()
    }

    fun seesTheFieldNotFocused(fieldTag: String) {
        test.onNodeWithTag(fieldTag).assertIsNotFocused()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onHearthScaffold(block: suspend HearthScaffoldRobot.() -> Unit) {
    HearthScaffoldRobot(this).block()
}
