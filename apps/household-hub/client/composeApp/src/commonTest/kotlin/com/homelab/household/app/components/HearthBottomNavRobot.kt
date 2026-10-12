package com.homelab.household.app.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.new_chat
import com.homelab.household.app.theme.DefaultSizes
import org.jetbrains.compose.resources.getString

/** The bottom navigation: a tab per place, and the centre plus that starts a new chat. */
@OptIn(ExperimentalTestApi::class)
class HearthBottomNavRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTab(tab: NavTab) {
        tabNode(tab).assertIsDisplayed()
    }

    suspend fun seesTheNewChatButton() {
        newChatNode().assertIsDisplayed()
    }

    suspend fun seesTabSelected(tab: NavTab) {
        tabNode(tab).assertIsSelected()
    }

    suspend fun seesTabNotSelected(tab: NavTab) {
        tabNode(tab).assertIsNotSelected()
    }

    suspend fun tapsTab(tab: NavTab) {
        tabNode(tab).performClick()
    }

    suspend fun tapsTheNewChatButton() {
        newChatNode().performClick()
    }

    suspend fun seesTabBigEnoughToHit(tab: NavTab) {
        tabNode(tab)
            .assertHeightIsAtLeast(DefaultSizes.touchTarget)
            .assertWidthIsAtLeast(DefaultSizes.touchTarget)
    }

    suspend fun seesTheNewChatButtonBigEnoughToHit() {
        newChatNode()
            .assertHeightIsAtLeast(DefaultSizes.touchTarget)
            .assertWidthIsAtLeast(DefaultSizes.touchTarget)
    }

    private suspend fun tabNode(tab: NavTab): SemanticsNodeInteraction {
        val label = getString(tab.label)
        test.waitUntilExactlyOneExists(hasText(label), timeoutMillis = WAIT_MILLIS)
        return test.onNodeWithText(label)
    }

    private suspend fun newChatNode(): SemanticsNodeInteraction {
        val description = getString(Res.string.new_chat)
        test.waitUntilExactlyOneExists(hasContentDescription(description), timeoutMillis = WAIT_MILLIS)
        return test.onNodeWithContentDescription(description)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onHearthBottomNav(block: suspend HearthBottomNavRobot.() -> Unit) {
    HearthBottomNavRobot(this).block()
}
