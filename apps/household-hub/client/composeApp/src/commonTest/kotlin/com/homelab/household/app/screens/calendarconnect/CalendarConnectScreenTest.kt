package com.homelab.household.app.screens.calendarconnect

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_apple_account
import com.homelab.household.app.resources.calendar_apple_password
import com.homelab.household.app.resources.calendar_apple_rejected
import com.homelab.household.app.resources.calendar_connect
import com.homelab.household.app.resources.calendar_google_account
import com.homelab.household.app.resources.calendar_google_password
import com.homelab.household.app.resources.calendar_other_account
import com.homelab.household.app.resources.calendar_other_password
import com.homelab.household.app.resources.calendar_password_rejected
import com.homelab.household.app.resources.calendar_server
import com.homelab.household.app.resources.calendar_server_missing
import com.homelab.household.app.resources.calendar_unreachable
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.domain.model.CalendarProvider
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Connecting a calendar, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class CalendarConnectScreenTest {
    private val wait = 5_000L

    /** A phone with somebody signed in on it: these screens are all behind a token. */
    private fun signedIn() = InMemorySessionStorage().apply { saveTokens("signed-in-token") }

    @Test
    fun details_the_hub_can_use_are_kept_and_the_screen_hands_back() {
        val hub = FakeCalendarHub()
        var connected = 0

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn()) {
                    CalendarConnectScreen(provider = CalendarProvider.APPLE, onBack = {}, onConnect = { connected++ })
                }
            }

            onNodeWithContentDescription(
                getString(Res.string.calendar_apple_account),
            ).performTextInput("emma@icloud.com")
            onNodeWithContentDescription(getString(Res.string.calendar_apple_password))
                .performTextInput("abcd-efgh-ijkl-mnop")
            onNodeWithText(getString(Res.string.calendar_connect)).performClick()

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
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn()) {
                    CalendarConnectScreen(provider = CalendarProvider.APPLE, onBack = {}, onConnect = { connected++ })
                }
            }

            onNodeWithContentDescription(
                getString(Res.string.calendar_apple_account),
            ).performTextInput("emma@icloud.com")
            onNodeWithContentDescription(getString(Res.string.calendar_apple_password)).performTextInput("my-apple-id")
            onNodeWithText(getString(Res.string.calendar_connect)).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.calendar_password_rejected)), timeoutMillis = wait)
            onNodeWithText(getString(Res.string.calendar_apple_rejected)).assertIsDisplayed()
            // The server was fine; blaming it would send the member to the wrong fix.
            onNodeWithText(getString(Res.string.calendar_unreachable)).assertDoesNotExist()
        }

        assertEquals(0, connected)
    }

    @Test
    fun a_server_the_hub_cannot_reach_lands_under_the_server_not_the_password() {
        val hub = FakeCalendarHub()
        hub.cannotReachTheServer()

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn()) {
                    CalendarConnectScreen(provider = CalendarProvider.OTHER, onBack = {}, onConnect = {})
                }
            }

            onNodeWithText(getString(Res.string.calendar_connect)).performClick()
            waitUntilExactlyOneExists(hasText(getString(Res.string.calendar_server_missing)), timeoutMillis = wait)

            onNodeWithContentDescription(getString(Res.string.calendar_other_account)).performTextInput("emma")
            onNodeWithContentDescription(getString(Res.string.calendar_other_password)).performTextInput("secret")
            onNodeWithContentDescription(getString(Res.string.calendar_server))
                .performTextInput("https://cloud.example.com/remote.php/dav")
            onNodeWithText(getString(Res.string.calendar_connect)).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.calendar_unreachable)), timeoutMillis = wait)
            onNodeWithText(getString(Res.string.calendar_password_rejected)).assertDoesNotExist()
        }

        // Tapping early asked the hub nothing; only the second, complete try reached it.
        assertEquals(1, hub.requests.size)
    }

    @Test
    fun google_shows_the_address_it_builds_from_the_account() {
        val hub = FakeCalendarHub()

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn()) {
                    CalendarConnectScreen(provider = CalendarProvider.GOOGLE, onBack = {}, onConnect = {})
                }
            }

            onNodeWithContentDescription(getString(Res.string.calendar_google_account))
                .performTextInput("emma.larsson@gmail.com")

            waitUntilExactlyOneExists(
                hasText("https://apidata.googleusercontent.com/caldav/v2/emma.larsson@gmail.com/events"),
                timeoutMillis = wait,
            )
            onNodeWithContentDescription(getString(Res.string.calendar_google_password)).assertIsDisplayed()
        }
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

                onNodeWithText(getString(Res.string.calendar_server)).assertExists()
            }
        }
    }
}
