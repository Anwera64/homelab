package com.homelab.household.app.screens.firstrun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.first_run_title
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.firstrun.AvatarPalette
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * First run with the real stack under it, down to the Ktor client; only the hub is faked.
 * Where `onCreated` and `onSignIn` take the user is `AppNavHostTest`.
 */
@OptIn(ExperimentalTestApi::class)
class FirstRunScreenTest {

    @Test
    fun creating_with_nothing_filled_in_says_what_is_missing_under_each_field() {
        val hub = FakeFirstRunHub()
        hub.createsTheHousehold()

        runScreenTest {
            firstRunScreen(hub)

            onFirstRun {
                seesTheForm()
                tapsCreate()
                seesTheNameIsMissing()
                seesThePinIsIncomplete()
            }
        }

        assertEquals(0, hub.registrations.size)
    }

    @Test
    fun creating_the_household_sends_the_name_pin_and_colour_and_goes_home() {
        val hub = FakeFirstRunHub()
        hub.createsTheHousehold()
        var created = 0

        runScreenTest {
            firstRunScreen(hub, onCreated = { created++ })

            onFirstRun {
                typesName("Emma")
                typesPin("482913")
                picksColour(2)
                tapsCreate()
            }
            waitUntil(timeoutMillis = WAIT_MILLIS) { created == 1 }
        }

        val sent = hub.registrations.single()
        assertTrue(sent.contains(""""full_name":"Emma""""), sent)
        assertTrue(sent.contains(""""pin":"482913""""), sent)
        assertTrue(sent.contains(""""avatar_color":"${AvatarPalette.swatches[1]}""""), sent)
    }

    @Test
    fun an_unreachable_hub_says_so_and_keeps_what_was_typed() {
        val hub = FakeFirstRunHub()
        hub.isOffline()
        var created = 0

        runScreenTest {
            firstRunScreen(hub, onCreated = { created++ })

            onFirstRun {
                typesName("Emma")
                typesPin("482913")
                tapsCreate()
                seesTheHubDidNotAnswer()
                stillSeesTheName("Emma")
            }
        }

        assertEquals(0, created)
    }

    @Test
    fun a_hub_someone_else_already_set_up_offers_sign_in_instead() {
        val hub = FakeFirstRunHub()
        hub.isAlreadySetUp()
        var signIn = 0

        runScreenTest {
            firstRunScreen(hub, onSignIn = { signIn++ })

            onFirstRun {
                typesName("Emma")
                typesPin("482913")
                tapsCreate()
                seesTheHubIsAlreadySetUp()
                tapsSignInInstead()
            }
            waitUntil(timeoutMillis = WAIT_MILLIS) { signIn == 1 }
        }

        assertEquals(1, signIn)
    }

    /** Every previewed state draws: the form's own copy is on screen whatever state it's in. */
    @Test
    fun every_previewed_state_draws() {
        val states = FirstRunUiStateProvider().values.toList()
        assertEquals(5, states.size)

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    HearthTheme(darkTheme = false) {
                        FirstRunContent(
                            state = state,
                            onNameChange = {},
                            onPinChange = {},
                            onColourSelect = {},
                            onCreate = {},
                            onSignIn = {}
                        )
                    }
                }

                onNodeWithText(getString(Res.string.first_run_title)).assertIsDisplayed()
            }
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
