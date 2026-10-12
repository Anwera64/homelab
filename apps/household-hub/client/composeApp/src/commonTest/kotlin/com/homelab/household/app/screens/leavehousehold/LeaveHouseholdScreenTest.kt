package com.homelab.household.app.screens.leavehousehold

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.presentation.leavehousehold.LeaveHouseholdStatus
import com.homelab.household.presentation.leavehousehold.LeaveHouseholdUiState
import kotlin.test.Test

/** Leaving the household, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class LeaveHouseholdScreenTest {
    @Test
    fun your_pin_leaves_the_household() {
        val hub = FakeMembersHub()
        hub.doesIt()
        var left = 0

        runScreenTest {
            leaveHouseholdScreen(hub, onLeft = { left++ })

            onLeaveHousehold {
                typesThePin("135790")
                tapsLeave()
            }
            waitUntil(timeoutMillis = 5_000L) { left == 1 }
        }
    }

    @Test
    fun a_wrong_pin_says_how_many_tries_are_left() {
        val hub = FakeMembersHub()
        hub.refusesThePin(attemptsLeft = 4)

        runScreenTest {
            leaveHouseholdScreen(hub)

            onLeaveHousehold {
                typesThePin("000000")
                tapsLeave()
                seesTriesLeft(4)
            }
        }
    }

    @Test
    fun the_only_admin_is_told_the_household_cannot_lose_them() {
        val hub = FakeMembersHub()
        hub.refusesTheOnlyAdmin()

        runScreenTest {
            leaveHouseholdScreen(hub)

            onLeaveHousehold {
                typesThePin("135790")
                tapsLeave()
                seesTheOnlyAdminCannotLeave()
            }
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
            leaveHouseholdContent(LeaveHouseholdUiState(pin = "135790", status = LeaveHouseholdStatus.Leaving))

            onLeaveHousehold {
                seesTheButtonDeleting()
                doesNotSeeLeave()
            }
        }

    @Test
    fun every_previewed_state_draws() {
        LeaveHouseholdUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                leaveHouseholdContent(state)

                onLeaveHousehold { seesTheTitle() }
            }
        }
    }
}
