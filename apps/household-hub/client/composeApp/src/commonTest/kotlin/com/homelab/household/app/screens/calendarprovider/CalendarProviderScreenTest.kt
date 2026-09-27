package com.homelab.household.app.screens.calendarprovider

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_apple
import com.homelab.household.app.resources.calendar_google
import com.homelab.household.app.resources.calendar_google_caption
import com.homelab.household.app.resources.calendar_other
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.domain.model.CalendarProvider
import org.jetbrains.compose.resources.getString
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
            setContent { StillTheme { CalendarProviderScreen(onBack = {}, onPick = { picked += it }) } }

            // WHEN
            onNodeWithText(getString(Res.string.calendar_apple)).performClick()

            // THEN
            assertEquals(listOf(CalendarProvider.APPLE), picked)
        }

    @Test
    fun `GIVEN the provider list WHEN another CalDAV server is tapped THEN its details open`() =
        runComposeUiTest {
            // GIVEN
            val picked = mutableListOf<CalendarProvider>()
            setContent { StillTheme { CalendarProviderScreen(onBack = {}, onPick = { picked += it }) } }

            // WHEN
            onNodeWithText(getString(Res.string.calendar_other)).performClick()

            // THEN
            assertEquals(listOf(CalendarProvider.OTHER), picked)
        }

    @Test
    fun `GIVEN the provider list WHEN Google is tapped THEN its sign-in opens`() =
        runComposeUiTest {
            // GIVEN
            val picked = mutableListOf<CalendarProvider>()
            setContent { StillTheme { CalendarProviderScreen(onBack = {}, onPick = { picked += it }) } }

            // WHEN
            onNodeWithText(getString(Res.string.calendar_google)).performClick()

            // THEN
            assertEquals(listOf(CalendarProvider.GOOGLE), picked)
        }

    @Test
    fun `GIVEN Google only takes a sign-in WHEN the list is shown THEN Google says so`() =
        runComposeUiTest {
            // GIVEN
            val caption = getString(Res.string.calendar_google_caption)

            // WHEN
            setContent { StillTheme { CalendarProviderScreen(onBack = {}, onPick = {}) } }

            // THEN
            onNodeWithText(caption).assertIsDisplayed()
            assertEquals("Sign in with Google", caption)
        }
}
