package com.homelab.household.app.screens.whenagentsact

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "When agents act", with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class WhenAgentsActScreenTest {
    private val wait = 5_000L

    @Test
    fun `GIVEN the hub's settings WHEN the screen opens THEN reading runs freely and only the undoable writes have switches`() {
        val hub = FakeMembersHub().apply { addsEventsAutomatically() }

        runScreenTest {
            whenAgentsActScreen(hub)

            onWhenAgentsAct {
                seesTheSettings()
                seesReadingRunFreely()
                seesAddingEventsIsAutomatic()
                seesChangingEventsAsks()
                seesNotesAsk()
                seesYourMemories()
                seesRememberingAsks()
                seesRemovingReplacingAndForgettingAlwaysAsk()
            }
        }
    }

    @Test
    fun `GIVEN adding events asks WHEN its switch is turned on THEN the hub has it automatic`() {
        val hub = FakeMembersHub()

        runScreenTest {
            whenAgentsActScreen(hub)

            onWhenAgentsAct {
                seesTheSettings()
                switchesAddingEvents()
            }

            waitUntil(timeoutMillis = wait) { hub.isAutomatic("calendar_write", "create") }
            onWhenAgentsAct { seesAddingEventsIsAutomatic() }
            assertTrue(hub.requests.any { it.contains("\"tool\":\"calendar_write\"") && it.contains("\"auto\":true") })
        }
    }

    @Test
    fun `GIVEN remembering asks WHEN its switch is turned on THEN the hub has it automatic`() {
        val hub = FakeMembersHub()

        runScreenTest {
            whenAgentsActScreen(hub)

            onWhenAgentsAct {
                seesTheSettings()
                switchesRemembering()
            }

            waitUntil(timeoutMillis = wait) { hub.isAutomatic("remember", "create") }
            onWhenAgentsAct { seesRememberingIsAutomatic() }
        }
    }

    @Test
    fun `GIVEN the hub stops taking changes WHEN a switch is turned on THEN it goes back and says why`() {
        val hub = FakeMembersHub().apply { refusesSettingChanges() }

        runScreenTest {
            whenAgentsActScreen(hub)

            onWhenAgentsAct {
                seesTheSettings()
                switchesChangingEvents()
                seesTheHubDidNotSave()
                seesChangingEventsAsks()
            }
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
                onWhenAgentsAct { seesTheTitle() }
            }
        }
    }
}
