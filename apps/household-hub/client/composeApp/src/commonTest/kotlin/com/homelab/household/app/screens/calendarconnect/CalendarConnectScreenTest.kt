package com.homelab.household.app.screens.calendarconnect

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.CalendarProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Connecting a calendar, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class CalendarConnectScreenTest {
    private val wait = 5_000L

    @Test
    fun details_the_hub_can_use_are_kept_and_the_screen_hands_back() {
        val hub = FakeCalendarHub()
        var connected = 0

        runScreenTest {
            calendarConnectScreen(hub, CalendarProvider.APPLE, onConnect = { connected++ })

            onCalendarConnect {
                typesTheAppleAccount("emma@icloud.com")
                typesTheApplePassword("abcd-efgh-ijkl-mnop")
                tapsConnect()
            }

            waitUntil(timeoutMillis = wait) { connected == 1 }
        }

        assertTrue(hub.requests.single().contains("\"provider\":\"apple_icloud\""))
        assertTrue(hub.requests.single().contains("\"url\":\"https://caldav.icloud.com\""))
    }

    @Test
    fun a_refused_password_lands_under_the_password_with_the_steps_to_make_one() {
        val hub = FakeCalendarHub()
        hub.rejectsThePassword()
        var connected = 0

        runScreenTest {
            calendarConnectScreen(hub, CalendarProvider.APPLE, onConnect = { connected++ })

            onCalendarConnect {
                typesTheAppleAccount("emma@icloud.com")
                typesTheApplePassword("my-apple-id")
                tapsConnect()

                seesThePasswordRejected()
                seesTheStepsToMakeAnApplePassword()
                // The server was fine; blaming it would send the member to the wrong fix.
                doesNotSeeTheServerUnreachable()
            }
        }

        assertEquals(0, connected)
    }

    @Test
    fun a_server_the_hub_cannot_reach_lands_under_the_server_not_the_password() {
        val hub = FakeCalendarHub()
        hub.cannotReachTheServer()

        runScreenTest {
            calendarConnectScreen(hub, CalendarProvider.OTHER)

            onCalendarConnect {
                tapsConnect()
                seesTheServerMissing()

                typesTheOtherAccount("emma")
                typesTheOtherPassword("secret")
                typesTheServer("https://cloud.example.com/remote.php/dav")
                tapsConnect()

                seesTheServerUnreachable()
                doesNotSeeThePasswordRejected()
            }
        }

        // Tapping early asked the hub nothing; only the second, complete try reached it.
        assertEquals(1, hub.requests.size)
    }

    @Test
    fun every_previewed_state_draws() {
        val states = CalendarConnectUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        CalendarConnectContent(
                            state = state,
                            onAccountChange = {},
                            onPasswordChange = {},
                            onServerChange = {},
                            onCalendarNameChange = {},
                            onConnect = {},
                            onOpenInstructions = {},
                            onBack = {},
                        )
                    }
                }

                onCalendarConnect { seesTheServerField() }
            }
        }
    }
}
