package com.homelab.household.app.screens.calendarprovider

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_apple
import com.homelab.household.app.resources.calendar_google
import com.homelab.household.app.resources.calendar_other
import com.homelab.household.app.resources.calendar_provider_title
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.domain.model.CalendarProvider
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where your calendar lives. Nothing here asks the hub, so no hub is faked. */
@OptIn(ExperimentalTestApi::class)
class CalendarProviderScreenTest {
    @Test
    fun each_provider_opens_its_own_details() =
        runComposeUiTest {
            val picked = mutableListOf<CalendarProvider>()
            setContent { StillTheme { CalendarProviderScreen(onBack = {}, onPick = { picked += it }) } }

            onNodeWithText(getString(Res.string.calendar_provider_title)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.calendar_apple)).performClick()
            onNodeWithText(getString(Res.string.calendar_google)).performClick()
            onNodeWithText(getString(Res.string.calendar_other)).performClick()

            assertEquals(listOf(CalendarProvider.APPLE, CalendarProvider.GOOGLE, CalendarProvider.OTHER), picked)
        }
}
