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
            val picked = mutableListOf<CalendarProvider>()
            calendarProviderScreen(onPick = { picked += it })

            onCalendarProvider { tapsApple() }

            assertEquals(listOf(CalendarProvider.APPLE), picked)
        }

    @Test
    fun `GIVEN the provider list WHEN another CalDAV server is tapped THEN its details open`() =
        runComposeUiTest {
            val picked = mutableListOf<CalendarProvider>()
            calendarProviderScreen(onPick = { picked += it })

            onCalendarProvider { tapsAnotherCalDavServer() }

            assertEquals(listOf(CalendarProvider.OTHER), picked)
        }

    @Test
    fun `GIVEN the provider list WHEN Google is tapped THEN its sign-in opens`() =
        runComposeUiTest {
            val picked = mutableListOf<CalendarProvider>()
            calendarProviderScreen(onPick = { picked += it })

            onCalendarProvider { tapsGoogle() }

            assertEquals(listOf(CalendarProvider.GOOGLE), picked)
        }

    @Test
    fun `GIVEN Google only takes a sign-in WHEN the list is shown THEN Google says so`() =
        runComposeUiTest {
            calendarProviderScreen()

            onCalendarProvider {
                assertEquals("Sign in with Google", seesGoogleOnlyTakesASignIn())
            }
        }
}
