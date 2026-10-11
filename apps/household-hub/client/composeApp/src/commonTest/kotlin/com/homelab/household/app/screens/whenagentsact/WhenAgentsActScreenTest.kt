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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_permission_calendar_add
import com.homelab.household.app.resources.tool_permission_calendar_change
import com.homelab.household.app.resources.tool_permission_calendar_remove
import com.homelab.household.app.resources.tool_permission_note_append
import com.homelab.household.app.resources.tool_permission_note_create
import com.homelab.household.app.resources.tool_permission_note_replace
import com.homelab.household.app.resources.when_agents_act_always_asks
import com.homelab.household.app.resources.when_agents_act_runs_freely
import com.homelab.household.app.resources.when_agents_act_save_unreachable
import com.homelab.household.app.resources.when_agents_act_title
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "When agents act", with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class WhenAgentsActScreenTest {
    private val wait = 5_000L

    private fun signedIn() = InMemorySessionStorage().apply { saveTokens("signed-in-token") }

    private fun ComposeUiTest.showSettings(
        hub: FakeMembersHub,
        onBack: () -> Unit = {},
    ) {
        setContent {
            TestApp(hub.engine, sessionStorage = signedIn()) {
                WhenAgentsActScreen(onBack = onBack)
            }
        }
    }

    private suspend fun ComposeUiTest.awaitSwitch(label: String) {
        waitUntilExactlyOneExists(hasContentDescription(label) and isToggleable(), timeoutMillis = wait)
    }

    @Test
    fun `GIVEN the hub's settings WHEN the screen opens THEN reading runs freely and only the undoable writes have switches`() {
        val hub = FakeMembersHub().apply { addsEventsAutomatically() }

        runScreenTest {
            showSettings(hub)
            awaitSwitch(getString(Res.string.tool_permission_calendar_add))

            onNodeWithText(getString(Res.string.when_agents_act_title)).assertIsDisplayed()
            assertEquals(
                3,
                onAllNodesWithText(getString(Res.string.when_agents_act_runs_freely)).fetchSemanticsNodes().size,
            )
            onNodeWithContentDescription(getString(Res.string.tool_permission_calendar_add)).assertIsOn()
            onNodeWithContentDescription(getString(Res.string.tool_permission_calendar_change)).assertIsOff()
            onNodeWithContentDescription(
                getString(Res.string.tool_permission_note_create),
            ).performScrollTo().assertIsOff()
            onNodeWithContentDescription(
                getString(Res.string.tool_permission_note_append),
            ).performScrollTo().assertIsOff()

            // Removing and replacing can't be undone from the chat: a lock, not a switch.
            onNodeWithText(getString(Res.string.tool_permission_calendar_remove)).performScrollTo().assertIsDisplayed()
            onNodeWithText(getString(Res.string.tool_permission_note_replace)).performScrollTo().assertIsDisplayed()
            assertEquals(
                2,
                onAllNodesWithText(getString(Res.string.when_agents_act_always_asks)).fetchSemanticsNodes().size,
            )
            assertEquals(
                0,
                onAllNodes(
                    hasContentDescription(getString(Res.string.tool_permission_calendar_remove)) and isToggleable(),
                ).fetchSemanticsNodes()
                    .size,
            )
        }
    }

    @Test
    fun `GIVEN adding events asks WHEN its switch is turned on THEN the hub has it automatic`() {
        val hub = FakeMembersHub()

        runScreenTest {
            showSettings(hub)
            val add = getString(Res.string.tool_permission_calendar_add)
            awaitSwitch(add)

            onNodeWithContentDescription(add).performClick()

            waitUntil(timeoutMillis = wait) { hub.isAutomatic("calendar_write", "create") }
            onNodeWithContentDescription(add).assertIsOn()
            assertTrue(hub.requests.any { it.contains("\"tool\":\"calendar_write\"") && it.contains("\"auto\":true") })
        }
    }

    @Test
    fun `GIVEN the hub stops taking changes WHEN a switch is turned on THEN it goes back and says why`() {
        val hub = FakeMembersHub().apply { refusesSettingChanges() }

        runScreenTest {
            showSettings(hub)
            val change = getString(Res.string.tool_permission_calendar_change)
            awaitSwitch(change)

            onNodeWithContentDescription(change).performClick()

            waitUntilExactlyOneExists(
                hasText(getString(Res.string.when_agents_act_save_unreachable)),
                timeoutMillis = wait,
            )
            onNodeWithContentDescription(change).assertIsOff()
            assertFalse(hub.isAutomatic("calendar_write", "update"))
        }
    }

    @Test
    fun every_previewed_state_draws() {
        val provider = WhenAgentsActUiStateProvider()
        provider.values.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        WhenAgentsActContent(state = state, onBack = {}, onToggle = {
                            _,
                            _,
                            _,
                            ->
                        }, onRetry = {})
                    }
                }
                onNodeWithText(getString(Res.string.when_agents_act_title)).assertIsDisplayed()
            }
        }
    }
}
