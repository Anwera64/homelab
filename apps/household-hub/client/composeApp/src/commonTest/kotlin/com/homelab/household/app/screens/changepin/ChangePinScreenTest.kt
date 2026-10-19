package com.homelab.household.app.screens.changepin

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.presentation.changepin.ChangePinStatus
import com.homelab.household.presentation.changepin.ChangePinUiState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Changing a PIN, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class ChangePinScreenTest {
    private val wait = 5_000L

    @Test
    fun `GIVEN the hub takes the change WHEN the member enters the current PIN and the new PIN twice and taps change THEN the screen hands back and this phone keeps a fresh token`() {
        val hub = FakeMembersHub()
        hub.takesTheChange()
        val tokens = InMemorySessionStorage().apply { saveTokens("old-token") }
        var changed = 0

        runScreenTest {
            changePinScreen(hub, tokens, onChange = { changed++ })

            onChangePin {
                typesTheCurrentPin("135790")
                typesTheNewPin("864209")
                typesTheNewPinAgain("864209")
                tapsChange()
            }

            waitUntil(timeoutMillis = wait) { changed == 1 }
        }

        assertEquals("fresh-token", tokens.getAccessToken())
    }

    @Test
    fun `GIVEN the hub refuses the current PIN WHEN the member taps change THEN the refusal shows under the current PIN with the attempts left`() {
        val hub = FakeMembersHub()
        hub.refusesThePin(attemptsLeft = 4)

        runScreenTest {
            changePinScreen(hub)

            onChangePin {
                typesTheCurrentPin("000000")
                typesTheNewPin("864209")
                typesTheNewPinAgain("864209")
                tapsChange()

                seesTheCurrentPinWrong(attemptsLeft = 4)
            }
        }
    }

    @Test
    fun `GIVEN two different new PINs WHEN the member taps change THEN the second field says they differ`() {
        val hub = FakeMembersHub()

        runScreenTest {
            changePinScreen(hub)

            onChangePin {
                typesTheCurrentPin("135790")
                typesTheNewPin("864209")
                typesTheNewPinAgain("864200")
                tapsChange()

                seesTheNewPinsDiffer()
            }
        }
    }

    /**
     * A PIN being saved signs out the other devices, so the wait is worth showing.
     * Nothing is dimmed: the button keeps its colour, stays enabled, and a screen reader hears what
     * is happening rather than "Working" (design notes §2, §6.21).
     */
    @Test
    fun `GIVEN a PIN being saved WHEN the screen is shown THEN the button shows it is saving`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    ChangePinContent(
                        state =
                            ChangePinUiState(
                                current = "135790",
                                new = "864209",
                                again = "864209",
                                status = ChangePinStatus.Saving,
                            ),
                        onCurrentChange = {},
                        onNewChange = {},
                        onAgainChange = {},
                        onChange = {},
                        onBack = {},
                    )
                }
            }

            onChangePin { seesItSavingOnTheButton() }
        }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the title is there`() {
        val states = ChangePinUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        ChangePinContent(
                            state = state,
                            onCurrentChange = {},
                            onNewChange = {},
                            onAgainChange = {},
                            onChange = {},
                            onBack = {},
                        )
                    }
                }

                onChangePin { seesTheTitle() }
            }
        }
    }
}
