package com.homelab.household.app.screens.firstrun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.presentation.firstrun.AvatarPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * First run with the real stack under it, down to the Ktor client; only the hub is faked.
 * Where `onCreate` and `onSignIn` take the user is `AppNavHostTest`.
 */
@OptIn(ExperimentalTestApi::class)
class FirstRunScreenTest {
    @Test
    fun `GIVEN an empty first run form WHEN create is tapped THEN each missing field says so and nothing is sent`() {
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
    fun `GIVEN a hub that creates the household WHEN the name and PIN and colour are filled in and create is tapped THEN they are sent to the hub and the screen hands back`() {
        val hub = FakeFirstRunHub()
        hub.createsTheHousehold()
        var created = 0

        runScreenTest {
            firstRunScreen(hub, onCreate = { created++ })

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
    fun `GIVEN an unreachable hub WHEN the form is filled in and create is tapped THEN it says the hub did not answer and keeps the name`() {
        val hub = FakeFirstRunHub()
        hub.isOffline()
        var created = 0

        runScreenTest {
            firstRunScreen(hub, onCreate = { created++ })

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
    fun `GIVEN a hub already set up WHEN the form is filled in and create is tapped THEN it says so and offers sign in instead`() {
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

    /**
     * On iOS, `KeyboardType.NumberPassword` draws a numeric keypad with no return key, so the
     * PIN field's `ImeAction.Done` has no button to sit on. Tapping the form itself, away from
     * any field, is the only dismissal path iOS offers — so it must clear focus.
     */
    @Test
    fun `GIVEN the first run form with the name focused WHEN the form background is tapped THEN the name loses focus`() {
        val hub = FakeFirstRunHub()
        hub.createsTheHousehold()

        runScreenTest {
            firstRunScreen(hub)

            onFirstRun {
                focusesTheName()
                seesTheNameIsFocused()
                tapsTheFormBackground()
                seesTheNameIsNotFocused()
            }
        }
    }

    /** Every previewed state draws: the form's own copy is on screen whatever state it's in. */
    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the title is there`() {
        val states = FirstRunUiStateProvider().values.toList()
        assertEquals(5, states.size)

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        FirstRunContent(
                            state = state,
                            onNameChange = {},
                            onPinChange = {},
                            onColourSelect = {},
                            onCreate = {},
                            onSignIn = {},
                        )
                    }
                }

                onFirstRun { seesTheTitle() }
            }
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
