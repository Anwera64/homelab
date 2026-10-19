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
    fun `GIVEN the reset code screen WHEN a code is typed and continue is tapped THEN the upper-cased code is carried on`() {
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
    fun `GIVEN a hub that lets the member in WHEN the new PIN is typed twice and set is tapped THEN the member is signed in and the token is kept`() {
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
    fun `GIVEN a hub that says the code has gone WHEN the new PIN is typed twice and set is tapped THEN the screen says the code has gone`() {
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
    fun `GIVEN two different new PINs WHEN set is tapped THEN the second field says the PINs differ`() {
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
    fun `GIVEN a new PIN being set WHEN the screen is shown THEN the button says it is setting`() =
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
    fun `GIVEN every previewed state of both screens WHEN each is drawn THEN each shows its own title`() {
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
