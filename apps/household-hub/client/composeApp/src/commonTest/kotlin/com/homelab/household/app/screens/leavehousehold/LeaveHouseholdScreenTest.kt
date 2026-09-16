package com.homelab.household.app.screens.leavehousehold

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.leave_pin_label
import com.homelab.household.app.resources.leave_sole_admin
import com.homelab.household.app.resources.leave_submit
import com.homelab.household.app.resources.leave_title
import com.homelab.household.app.resources.leave_wrong_pin
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.data.local.InMemoryTokenStorage
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

/** Leaving the household, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class LeaveHouseholdScreenTest {

    private val wait = 5_000L

    /** A phone with somebody signed in on it: these screens are all behind a token. */
    private fun signedIn() = InMemoryTokenStorage().apply { saveTokens("signed-in-token") }

    @Test
    fun your_pin_leaves_the_household() {
        val hub = FakeMembersHub()
        hub.doesIt()
        var left = 0

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { LeaveHouseholdScreen(onBack = {}, onLeft = { left++ }) } }

            onNodeWithContentDescription(getString(Res.string.leave_pin_label)).performTextInput("135790")
            onNodeWithText(getString(Res.string.leave_submit)).performClick()

            waitUntil(timeoutMillis = wait) { left == 1 }
        }
    }

    @Test
    fun a_wrong_pin_says_how_many_tries_are_left() {
        val hub = FakeMembersHub()
        hub.refusesThePin(attemptsLeft = 4)

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { LeaveHouseholdScreen(onBack = {}, onLeft = {}) } }

            onNodeWithContentDescription(getString(Res.string.leave_pin_label)).performTextInput("000000")
            onNodeWithText(getString(Res.string.leave_submit)).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.leave_wrong_pin, 4)), timeoutMillis = wait)
        }
    }

    @Test
    fun the_only_admin_is_told_the_household_cannot_lose_them() {
        val hub = FakeMembersHub()
        hub.refusesTheOnlyAdmin()

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { LeaveHouseholdScreen(onBack = {}, onLeft = {}) } }

            onNodeWithContentDescription(getString(Res.string.leave_pin_label)).performTextInput("135790")
            onNodeWithText(getString(Res.string.leave_submit)).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.leave_sole_admin)), timeoutMillis = wait)
        }
    }

    @Test
    fun every_previewed_state_draws() {
        LeaveHouseholdUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                setContent {
                    HearthTheme(darkTheme = false) {
                        LeaveHouseholdContent(state = state, onPinChange = {}, onLeave = {}, onBack = {})
                    }
                }

                onNodeWithText(getString(Res.string.leave_title)).assertIsDisplayed()
            }
        }
    }
}
