package com.homelab.household.app.screens.pinapprove

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.pinapprove.PinApproveStatus
import com.homelab.household.presentation.pinapprove.PinApproveUiState
import kotlin.test.Test

/** Vouching for a housemate, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class PinApproveScreenTest {
    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")

    @Test
    fun your_own_pin_produces_the_code_to_read_out() {
        val hub = FakeMembersHub()
        hub.approvesResets(code = "P4XN7T")

        runScreenTest {
            pinApproveScreen(hub, emma)

            onPinApprove {
                typesYourPin("246801")
                tapsGenerate()
                seesTheCode("P4XN7T")
            }
        }
    }

    @Test
    fun a_wrong_pin_of_your_own_says_how_many_tries_are_left() {
        val hub = FakeMembersHub()
        hub.refusesThePin(attemptsLeft = 3)

        runScreenTest {
            pinApproveScreen(hub, emma)

            onPinApprove {
                typesYourPin("000000")
                tapsGenerate()
                seesTriesLeft(3)
            }
        }
    }

    /**
     * Approving checks your own PIN first, and the button says which PIN is being checked.
     * Nothing is dimmed: the button keeps its colour, stays enabled, and a screen reader hears what
     * is happening rather than "Working" (design notes §2, §6.21).
     */
    @Test
    fun checking_your_pin_shows_on_the_button() =
        runComposeUiTest {
            pinApproveContent(
                PinApproveUiState(
                    member = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E"),
                    pin = "246801",
                    status = PinApproveStatus.Checking,
                ),
            )

            onPinApprove {
                seesTheButtonChecking()
                doesNotSeeGenerate()
            }
        }

    @Test
    fun every_previewed_state_draws() {
        PinApproveUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                pinApproveContent(state)

                onPinApprove { seesTheTitleFor(state.member.name) }
            }
        }
    }
}
