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
    fun `GIVEN a hub that accepts the PIN WHEN the PIN is typed and leave is tapped THEN the screen reports that the member left`() {
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
    fun `GIVEN a hub that refuses the PIN WHEN the PIN is typed and leave is tapped THEN the screen says how many tries are left`() {
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
    fun `GIVEN a hub that refuses the only admin WHEN the PIN is typed and leave is tapped THEN the screen says the only admin cannot leave`() {
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
    fun `GIVEN a leave in progress WHEN the screen is shown THEN the button says it is deleting and leave is gone`() =
        runComposeUiTest {
            leaveHouseholdContent(LeaveHouseholdUiState(pin = "135790", status = LeaveHouseholdStatus.Leaving))

            onLeaveHousehold {
                seesTheButtonDeleting()
                doesNotSeeLeave()
            }
        }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the title is there`() {
        LeaveHouseholdUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                leaveHouseholdContent(state)

                onLeaveHousehold { seesTheTitle() }
            }
        }
    }
}
