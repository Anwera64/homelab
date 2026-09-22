package com.homelab.household.app.screens.pinentry

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_pin_checking
import com.homelab.household.app.resources.pin_title
import com.homelab.household.app.testing.FakeSignInHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.pinentry.PinEntryUiState
import com.homelab.household.presentation.pinentry.PinStatus
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The PIN pad with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class PinEntryScreenTest {
    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")

    @Test
    fun the_right_pin_signs_the_member_in() {
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
    fun delete_takes_the_last_digit_back() {
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
    fun two_tries_from_a_wait_says_so() {
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
    fun a_locked_member_is_told_to_wait() {
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
    fun an_unreachable_hub_says_so() {
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
    fun back_goes_back_to_the_picker() {
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
    fun checking_says_the_pin_is_with_the_hub() =
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

            onNodeWithTag(PIN_DOTS_TAG).assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_pin_checking),
                ),
            )
        }

    @Test
    fun every_previewed_state_draws() {
        val states = PinEntryUiStateProvider().values.toList()
        assertEquals(7, states.size)

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        PinEntryContent(state = state, onDigit = {}, onDelete = {}, onBack = {}, onForget = {})
                    }
                }

                onNodeWithText(getString(Res.string.pin_title, state.member.name)).assertIsDisplayed()
            }
        }
    }

    @Test
    fun forgotten_it_leads_to_getting_a_new_pin() {
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
