package com.homelab.household.app.screens.leavehousehold

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.components.HEARTH_PROGRESS_BAR_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_leave_deleting
import com.homelab.household.app.resources.leave_deleting
import com.homelab.household.app.resources.leave_pin_label
import com.homelab.household.app.resources.leave_sole_admin
import com.homelab.household.app.resources.leave_submit
import com.homelab.household.app.resources.leave_title
import com.homelab.household.app.resources.leave_wrong_pin
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.presentation.leavehousehold.LeaveHouseholdStatus
import com.homelab.household.presentation.leavehousehold.LeaveHouseholdUiState
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

/** Leaving the household, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class LeaveHouseholdScreenTest {
    private val wait = 5_000L

    /** A phone with somebody signed in on it: these screens are all behind a token. */
    private fun signedIn() = InMemorySessionStorage().apply { saveTokens("signed-in-token") }

    @Test
    fun your_pin_leaves_the_household() {
        val hub = FakeMembersHub()
        hub.doesIt()
        var left = 0

        runScreenTest {
            setContent {
                TestApp(
                    hub.engine,
                    sessionStorage = signedIn(),
                ) { LeaveHouseholdScreen(onBack = {}, onLeft = { left++ }) }
            }

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
            setContent {
                TestApp(
                    hub.engine,
                    sessionStorage = signedIn(),
                ) { LeaveHouseholdScreen(onBack = {}, onLeft = {}) }
            }

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
            setContent {
                TestApp(
                    hub.engine,
                    sessionStorage = signedIn(),
                ) { LeaveHouseholdScreen(onBack = {}, onLeft = {}) }
            }

            onNodeWithContentDescription(getString(Res.string.leave_pin_label)).performTextInput("135790")
            onNodeWithText(getString(Res.string.leave_submit)).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.leave_sole_admin)), timeoutMillis = wait)
        }
    }

    /**
     * The most destructive action in the app, so it says plainly that it is under way.
     * Nothing is dimmed: the button keeps its colour, stays enabled, and a screen reader hears what
     * is happening rather than "Working" (design notes §2, §6.21).
     */
    @Test
    fun deleting_the_account_shows_on_the_button() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    LeaveHouseholdContent(
                        state = LeaveHouseholdUiState(pin = "135790", status = LeaveHouseholdStatus.Leaving),
                        onPinChange = {},
                        onLeave = {},
                        onBack = {},
                    )
                }
            }

            onNodeWithText(getString(Res.string.leave_deleting))
                .assertIsEnabled()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        getString(Res.string.a11y_leave_deleting),
                    ),
                )
            onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertIsDisplayed()
            onNodeWithText(getString(Res.string.leave_submit)).assertDoesNotExist()
        }

    @Test
    fun every_previewed_state_draws() {
        LeaveHouseholdUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        LeaveHouseholdContent(state = state, onPinChange = {}, onLeave = {}, onBack = {})
                    }
                }

                onNodeWithText(getString(Res.string.leave_title)).assertIsDisplayed()
            }
        }
    }
}
