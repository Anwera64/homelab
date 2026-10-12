package com.homelab.household.app.screens.calendarprovider

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_apple
import com.homelab.household.app.resources.calendar_google
import com.homelab.household.app.resources.calendar_google_caption
import com.homelab.household.app.resources.calendar_other
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.domain.model.CalendarProvider
import org.jetbrains.compose.resources.getString

/** Everything the calendar provider list says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class CalendarProviderRobot(
    private val test: ComposeUiTest,
) {
    suspend fun tapsApple() {
        test.onNodeWithText(getString(Res.string.calendar_apple)).performClick()
    }

    suspend fun tapsAnotherCalDavServer() {
        test.onNodeWithText(getString(Res.string.calendar_other)).performClick()
    }

    suspend fun tapsGoogle() {
        test.onNodeWithText(getString(Res.string.calendar_google)).performClick()
    }

    /** Google's row says it only takes a sign-in; returns the words it says. */
    suspend fun seesGoogleOnlyTakesASignIn(): String {
        val caption = getString(Res.string.calendar_google_caption)
        test.waitUntilExactlyOneExists(hasText(caption), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(caption).assertIsDisplayed()
        return caption
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onCalendarProvider(block: suspend CalendarProviderRobot.() -> Unit) {
    CalendarProviderRobot(this).block()
}

/** The provider list, with every pick handed to [onPick]. Nothing here asks the hub. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.calendarProviderScreen(onPick: (CalendarProvider) -> Unit = {}) {
    setContent { StillTheme { CalendarProviderScreen(onBack = {}, onPick = onPick) } }
}
