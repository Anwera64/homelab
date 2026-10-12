package com.homelab.household.app.screens.resetpin

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeJoinHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.presentation.resetpin.NewPinStatus
import com.homelab.household.presentation.resetpin.NewPinUiState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Redeeming a reset code, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class ResetPinScreenTest {
    private val wait = 5_000L

    @Test
    fun the_code_is_carried_to_the_new_pin_screen() {
        val hub = FakeJoinHub()
        val codes = mutableListOf<String>()

        runScreenTest {
            resetCodeScreen(hub, onCode = { codes += it })

            onResetPin {
                typesTheResetCode("p4xn7t")
                tapsContinue()
            }

            waitUntil(timeoutMillis = wait) { codes.size == 1 }
        }

        assertEquals("P4XN7T", codes.single())
    }

    @Test
    fun the_new_pin_signs_the_member_in() {
        val hub = FakeJoinHub()
        hub.letsThemJoinAs(memberId = "emma", fullName = "Emma")
        val tokens = InMemorySessionStorage()
        var signedIn = 0

        runScreenTest {
            newPinScreen(hub, code = "P4XN7T", tokens = tokens, onSignedIn = { signedIn++ })

            onResetPin {
                typesTheNewPin("864209")
                typesTheNewPinAgain("864209")
                tapsSetThePin()
            }

            waitUntil(timeoutMillis = wait) { signedIn == 1 }
        }

        assertEquals("joined-token", tokens.getAccessToken())
    }

    @Test
    fun a_code_that_has_gone_says_so() {
        val hub = FakeJoinHub()
        hub.saysTheCodeHasGone()

        runScreenTest {
            newPinScreen(hub, code = "P4XN7T")

            onResetPin {
                typesTheNewPin("864209")
                typesTheNewPinAgain("864209")
                tapsSetThePin()

                seesTheCodeHasGone()
            }
        }
    }

    @Test
    fun two_different_pins_say_so_under_the_second_field() {
        val hub = FakeJoinHub()

        runScreenTest {
            newPinScreen(hub, code = "P4XN7T")

            onResetPin {
                typesTheNewPin("864209")
                typesTheNewPinAgain("864200")
                tapsSetThePin()

                seesThePinsDiffer()
            }
        }
    }

    /**
     * The end of a PIN recovery: the code has been accepted and the new PIN is going to the hub.
     * Nothing is dimmed, and the button says which PIN is being set.
     */
    @Test
    fun setting_the_new_pin_shows_on_the_button() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    NewPinContent(
                        state = NewPinUiState(pin = "864209", again = "864209", status = NewPinStatus.Setting),
                        onPinChange = {},
                        onAgainChange = {},
                        onSetPin = {},
                    )
                }
            }

            onResetPin { seesItSettingOnTheButton() }
        }

    @Test
    fun every_previewed_state_draws() {
        ResetCodeUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        ResetCodeContent(state = state, onCodeChange = {}, onContinue = {}, onBack = {})
                    }
                }

                onResetPin { seesTheResetCodeTitle() }
            }
        }

        NewPinUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        NewPinContent(state = state, onPinChange = {}, onAgainChange = {}, onSetPin = {})
                    }
                }

                onResetPin { seesTheNewPinTitle() }
            }
        }
    }
}
