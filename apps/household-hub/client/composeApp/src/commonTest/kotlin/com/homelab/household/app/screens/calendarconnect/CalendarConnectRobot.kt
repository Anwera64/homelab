package com.homelab.household.app.screens.calendarconnect

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.calendar_apple_account
import com.homelab.household.app.resources.calendar_apple_password
import com.homelab.household.app.resources.calendar_apple_rejected
import com.homelab.household.app.resources.calendar_connect
import com.homelab.household.app.resources.calendar_other_account
import com.homelab.household.app.resources.calendar_other_password
import com.homelab.household.app.resources.calendar_password_rejected
import com.homelab.household.app.resources.calendar_server
import com.homelab.household.app.resources.calendar_server_missing
import com.homelab.household.app.resources.calendar_unreachable
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.domain.model.CalendarProvider
import org.jetbrains.compose.resources.getString

/** Everything the calendar connection screen says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class CalendarConnectRobot(
    private val test: ComposeUiTest,
) {
    suspend fun typesTheAppleAccount(account: String) {
        test.onNodeWithContentDescription(getString(Res.string.calendar_apple_account)).performTextInput(account)
    }

    suspend fun typesTheApplePassword(password: String) {
        test.onNodeWithContentDescription(getString(Res.string.calendar_apple_password)).performTextInput(password)
    }

    suspend fun typesTheOtherAccount(account: String) {
        test.onNodeWithContentDescription(getString(Res.string.calendar_other_account)).performTextInput(account)
    }

    suspend fun typesTheOtherPassword(password: String) {
        test.onNodeWithContentDescription(getString(Res.string.calendar_other_password)).performTextInput(password)
    }

    suspend fun typesTheServer(address: String) {
        test.onNodeWithContentDescription(getString(Res.string.calendar_server)).performTextInput(address)
    }

    suspend fun tapsConnect() {
        test.onNodeWithText(getString(Res.string.calendar_connect)).performClick()
    }

    suspend fun seesThePasswordRejected() = seesText(getString(Res.string.calendar_password_rejected))

    suspend fun doesNotSeeThePasswordRejected() {
        test.onNodeWithText(getString(Res.string.calendar_password_rejected)).assertDoesNotExist()
    }

    suspend fun seesTheStepsToMakeAnApplePassword() = seesText(getString(Res.string.calendar_apple_rejected))

    suspend fun seesTheServerMissing() = seesText(getString(Res.string.calendar_server_missing))

    suspend fun seesTheServerUnreachable() = seesText(getString(Res.string.calendar_unreachable))

    suspend fun doesNotSeeTheServerUnreachable() {
        test.onNodeWithText(getString(Res.string.calendar_unreachable)).assertDoesNotExist()
    }

    suspend fun seesTheServerField() {
        val text = getString(Res.string.calendar_server)
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertExists()
    }

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onCalendarConnect(block: suspend CalendarConnectRobot.() -> Unit) {
    CalendarConnectRobot(this).block()
}

/** The connection screen for [provider], signed in, over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.calendarConnectScreen(
    hub: FakeCalendarHub,
    provider: CalendarProvider,
    onConnect: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine, sessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") }) {
            CalendarConnectScreen(provider = provider, onBack = {}, onConnect = onConnect)
        }
    }
}
