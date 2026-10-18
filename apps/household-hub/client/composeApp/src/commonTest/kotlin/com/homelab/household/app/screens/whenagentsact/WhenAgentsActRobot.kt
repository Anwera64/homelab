package com.homelab.household.app.screens.whenagentsact

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_permission_calendar_add
import com.homelab.household.app.resources.tool_permission_calendar_change
import com.homelab.household.app.resources.tool_permission_calendar_remove
import com.homelab.household.app.resources.tool_permission_forget
import com.homelab.household.app.resources.tool_permission_note_append
import com.homelab.household.app.resources.tool_permission_note_create
import com.homelab.household.app.resources.tool_permission_note_replace
import com.homelab.household.app.resources.tool_permission_remember
import com.homelab.household.app.resources.when_agents_act_always_asks
import com.homelab.household.app.resources.when_agents_act_memories
import com.homelab.household.app.resources.when_agents_act_runs_freely
import com.homelab.household.app.resources.when_agents_act_save_unreachable
import com.homelab.household.app.resources.when_agents_act_title
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.test.assertEquals

/** Everything "When agents act" says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class WhenAgentsActRobot(
    private val test: ComposeUiTest,
) {
    /** The writes have come from the hub once their first switch is drawn. */
    suspend fun seesTheSettings() {
        test.waitUntilExactlyOneExists(
            switch(getString(Res.string.tool_permission_calendar_add)),
            timeoutMillis = WAIT_MILLIS,
        )
        test.onNodeWithText(getString(Res.string.when_agents_act_title)).assertIsDisplayed()
    }

    suspend fun seesTheTitle() {
        val title = getString(Res.string.when_agents_act_title)
        test.waitUntilExactlyOneExists(hasText(title), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(title).assertIsDisplayed()
    }

    suspend fun seesReadingRunFreely() {
        assertEquals(3, count(getString(Res.string.when_agents_act_runs_freely)))
    }

    suspend fun seesAddingEventsIsAutomatic() = switchOf(Res.string.tool_permission_calendar_add).assertIsOn()

    suspend fun seesAddingEventsAsks() = switchOf(Res.string.tool_permission_calendar_add).assertIsOff()

    suspend fun seesChangingEventsAsks() = switchOf(Res.string.tool_permission_calendar_change).assertIsOff()

    suspend fun seesNotesAsk() {
        switchOf(Res.string.tool_permission_note_create).performScrollTo().assertIsOff()
        switchOf(Res.string.tool_permission_note_append).performScrollTo().assertIsOff()
    }

    /** Removing, replacing and forgetting can't be undone from the chat: a lock, never a switch. */
    suspend fun seesRemovingReplacingAndForgettingAlwaysAsk() {
        val locked =
            listOf(
                Res.string.tool_permission_calendar_remove,
                Res.string.tool_permission_note_replace,
                Res.string.tool_permission_forget,
            )
        locked.forEach { label ->
            test.onNodeWithText(getString(label)).performScrollTo().assertIsDisplayed()
            assertEquals(0, test.onAllNodes(switch(getString(label))).fetchSemanticsNodes().size)
        }
        assertEquals(locked.size, count(getString(Res.string.when_agents_act_always_asks)))
    }

    suspend fun seesYourMemories() {
        // Each card's heading is set in capitals.
        val heading = getString(Res.string.when_agents_act_memories).uppercase()
        test.onNodeWithText(heading).performScrollTo().assertIsDisplayed()
    }

    suspend fun seesRememberingAsks() = switchOf(Res.string.tool_permission_remember).performScrollTo().assertIsOff()

    suspend fun seesRememberingIsAutomatic() =
        switchOf(Res.string.tool_permission_remember).performScrollTo().assertIsOn()

    suspend fun switchesRemembering() = switchOf(Res.string.tool_permission_remember).performScrollTo().performClick()

    suspend fun switchesAddingEvents() = switchOf(Res.string.tool_permission_calendar_add).performClick()

    suspend fun switchesChangingEvents() = switchOf(Res.string.tool_permission_calendar_change).performClick()

    suspend fun seesTheHubDidNotSave() {
        val text = getString(Res.string.when_agents_act_save_unreachable)
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private suspend fun switchOf(label: StringResource) = test.onNode(switch(getString(label)))

    private fun switch(label: String) = hasContentDescription(label) and isToggleable()

    private fun count(text: String) = test.onAllNodesWithText(text).fetchSemanticsNodes().size

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onWhenAgentsAct(block: suspend WhenAgentsActRobot.() -> Unit) {
    WhenAgentsActRobot(this).block()
}

/** "When agents act" for a signed-in member, over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.whenAgentsActScreen(
    hub: FakeMembersHub,
    onBack: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine, sessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") }) {
            WhenAgentsActScreen(onBack = onBack)
        }
    }
}
