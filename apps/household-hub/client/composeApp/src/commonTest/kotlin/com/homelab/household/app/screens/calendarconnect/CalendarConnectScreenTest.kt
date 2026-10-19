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
    fun `GIVEN an Apple calendar the hub accepts WHEN the member types the account and password and connects THEN the details reach the hub and the screen hands back`() {
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
    fun `GIVEN the hub refuses the Apple password WHEN the member connects THEN the refusal shows under the password with the steps to make one`() {
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
    fun `GIVEN a CalDAV server the hub cannot reach WHEN the member connects with complete details THEN the error shows under the server and not the password`() {
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
    fun `GIVEN every previewed state WHEN it is drawn THEN the server field is there`() {
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
