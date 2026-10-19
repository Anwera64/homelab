package com.homelab.household.app.screens.pinentry

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeSignInHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.pinentry.PinEntryUiState
import com.homelab.household.presentation.pinentry.PinStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The PIN pad with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class PinEntryScreenTest {
    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")

    @Test
    fun `GIVEN a hub that accepts Emma's PIN WHEN the PIN is typed THEN Emma is signed in and the hub got her id and PIN`() {
        val hub = FakeSignInHub()
        hub.acceptsThePinOf(emma)
        var signedIn = 0

        runScreenTest {
            pinEntryScreen(hub, emma, onSignedIn = { signedIn++ })

            onPinPad {
                seesThePinPadOf("Emma")
                typesPin("482913")
            }
            waitUntil(timeoutMillis = WAIT_MILLIS) { signedIn == 1 }
        }

        val sent = hub.signIns.single()
        assertTrue(sent.contains(""""user_id":"emma""""), sent)
        assertTrue(sent.contains(""""pin":"482913""""), sent)
    }

    @Test
    fun `GIVEN three digits typed WHEN delete is tapped THEN two digits remain entered and nothing is sent`() {
        val hub = FakeSignInHub()

        runScreenTest {
            pinEntryScreen(hub, emma)

            onPinPad {
                typesPin("482")
                tapsDelete()
                seesDigitsEntered(2)
            }
        }

        assertEquals(0, hub.signIns.size)
    }

    @Test
    fun `GIVEN a hub that refuses the PIN with two tries left WHEN a PIN is typed THEN the pad says two tries are left and clears the digits`() {
        val hub = FakeSignInHub()
        hub.refusesThePin(attemptsLeft = 2)
        var signedIn = 0

        runScreenTest {
            pinEntryScreen(hub, emma, onSignedIn = { signedIn++ })

            onPinPad {
                typesPin("000000")
                seesAttemptsLeft(2)
                seesDigitsEntered(0)
            }
        }

        assertEquals(0, signedIn)
    }

    @Test
    fun `GIVEN a hub that locks the member WHEN a PIN is typed THEN the pad says it is locked`() {
        val hub = FakeSignInHub()
        hub.locksFor(seconds = 30)

        runScreenTest {
            pinEntryScreen(hub, emma)

            onPinPad {
                typesPin("000000")
                seesItIsLocked()
            }
        }
    }

    @Test
    fun `GIVEN an unreachable hub WHEN a PIN is typed THEN the pad says the hub did not answer`() {
        val hub = FakeSignInHub()
        hub.isOffline()

        runScreenTest {
            pinEntryScreen(hub, emma)

            onPinPad {
                typesPin("482913")
                seesTheHubDidNotAnswer()
            }
        }
    }

    @Test
    fun `GIVEN the PIN pad WHEN back is tapped THEN the screen goes back`() {
        val hub = FakeSignInHub()
        var back = 0

        runScreenTest {
            pinEntryScreen(hub, emma, onBack = { back++ })

            onPinPad { tapsBack() }
            waitUntil(timeoutMillis = WAIT_MILLIS) { back == 1 }
        }
    }

    /**
     * The pad has no submit button, so nothing here can carry a bar — the dots do it instead
     * (design notes §6.21, pattern 5). This asserts the pad *says* it is working, never a frame of
     * the wave: under `StillTheme` the dots stand still, which is the only reason the test can run
     * at all.
     */
    @Test
    fun `GIVEN a PIN being checked WHEN the pad is shown THEN it says the PIN is with the hub`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    PinEntryContent(
                        state = PinEntryUiState(member = emma, entered = 6, status = PinStatus.Checking),
                        onDigit = {},
                        onDelete = {},
                        onBack = {},
                        onForget = {},
                    )
                }
            }

            onPinPad { seesThePinBeingChecked() }
        }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the pad names the member`() {
        val states = PinEntryUiStateProvider().values.toList()
        assertEquals(7, states.size)

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        PinEntryContent(state = state, onDigit = {}, onDelete = {}, onBack = {}, onForget = {})
                    }
                }

                onPinPad { seesThePinPadOf(state.member.name) }
            }
        }
    }

    @Test
    fun `GIVEN the PIN pad WHEN forgotten is tapped THEN the screen reports the PIN as forgotten`() {
        val hub = FakeSignInHub()
        var forgotten = 0

        runScreenTest {
            pinEntryScreen(hub, emma, onForget = { forgotten++ })

            onPinPad { tapsForgotten() }
            waitUntil(timeoutMillis = WAIT_MILLIS) { forgotten == 1 }
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
