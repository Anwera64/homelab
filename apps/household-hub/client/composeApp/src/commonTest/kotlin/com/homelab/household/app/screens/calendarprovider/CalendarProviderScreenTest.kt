package com.homelab.household.app.screens.calendarprovider

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.domain.model.CalendarProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where your calendar lives. Nothing here asks the hub, so no hub is faked. */
@OptIn(ExperimentalTestApi::class)
class CalendarProviderScreenTest {
    @Test
    fun `GIVEN the provider list WHEN Apple is tapped THEN Apple's details open`() =
        runComposeUiTest {
            // GIVEN
            val picked = mutableListOf<CalendarProvider>()
            calendarProviderScreen(onPick = { picked += it })

            // WHEN
            onCalendarProvider { tapsApple() }

            // THEN
            assertEquals(listOf(CalendarProvider.APPLE), picked)
        }

    @Test
    fun `GIVEN the provider list WHEN another CalDAV server is tapped THEN its details open`() =
        runComposeUiTest {
            // GIVEN
            val picked = mutableListOf<CalendarProvider>()
            calendarProviderScreen(onPick = { picked += it })

            // WHEN
            onCalendarProvider { tapsAnotherCalDavServer() }

            // THEN
            assertEquals(listOf(CalendarProvider.OTHER), picked)
        }

    @Test
    fun `GIVEN the provider list WHEN Google is tapped THEN its sign-in opens`() =
        runComposeUiTest {
            // GIVEN
            val picked = mutableListOf<CalendarProvider>()
            calendarProviderScreen(onPick = { picked += it })

            // WHEN
            onCalendarProvider { tapsGoogle() }

            // THEN
            assertEquals(listOf(CalendarProvider.GOOGLE), picked)
        }

    @Test
    fun `GIVEN Google only takes a sign-in WHEN the list is shown THEN Google says so`() =
        runComposeUiTest {
            // WHEN
            calendarProviderScreen()

            // THEN
            onCalendarProvider {
                assertEquals("Sign in with Google", seesGoogleOnlyTakesASignIn())
            }
        }
}
