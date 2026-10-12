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
    fun the_new_pin_is_taken_and_this_phone_keeps_its_place() {
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
    fun a_wrong_current_pin_lands_under_that_field() {
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
    fun two_different_new_pins_say_so_under_the_second_field() {
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
    fun saving_the_new_pin_shows_on_the_button() =
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
    fun every_previewed_state_draws() {
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
