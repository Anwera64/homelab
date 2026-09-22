package com.homelab.household.app.screens.pinentry

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.pin_attempts_left
import com.homelab.household.app.resources.pin_back
import com.homelab.household.app.resources.pin_delete
import com.homelab.household.app.resources.pin_entered
import com.homelab.household.app.resources.pin_forgotten
import com.homelab.household.app.resources.pin_locked
import com.homelab.household.app.resources.pin_title
import com.homelab.household.app.resources.pin_unreachable
import com.homelab.household.app.testing.FakeSignInHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.domain.model.Member
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** Everything the PIN pad says and does, named as the resources the screen reads. */
@OptIn(ExperimentalTestApi::class)
class PinEntryRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesThePinPadOf(name: String) = seesText(getString(Res.string.pin_title, name))

    /** Taps the keys one digit at a time, the way a person would. */
    fun typesPin(pin: String) {
        pin.forEach { digit -> test.onNodeWithText(digit.toString()).performClick() }
    }

    suspend fun tapsDelete() {
        test.onNodeWithContentDescription(getString(Res.string.pin_delete)).performClick()
    }

    suspend fun tapsForgotten() {
        test.onNodeWithText(getString(Res.string.pin_forgotten)).performClick()
    }

    suspend fun tapsBack() {
        test.onNodeWithContentDescription(getString(Res.string.pin_back)).performClick()
    }

    suspend fun seesDigitsEntered(count: Int) {
        val description = getPluralString(Res.plurals.pin_entered, count, count)
        test.waitUntilExactlyOneExists(
            androidx.compose.ui.test
                .hasContentDescription(description),
            timeoutMillis = WAIT_MILLIS,
        )
    }

    suspend fun seesAttemptsLeft(count: Int) = seesText(getPluralString(Res.plurals.pin_attempts_left, count, count))

    suspend fun seesItIsLocked() = seesText(getString(Res.string.pin_locked))

    suspend fun seesTheHubDidNotAnswer() = seesText(getString(Res.string.pin_unreachable))

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onPinPad(block: suspend PinEntryRobot.() -> Unit) {
    PinEntryRobot(this).block()
}

/** The PIN pad of [member] over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.pinEntryScreen(
    hub: FakeSignInHub,
    member: Member,
    onSignedIn: () -> Unit = {},
    onBack: () -> Unit = {},
    onForgotten: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine) {
            PinEntryScreen(
                member = member,
                onSignedIn = onSignedIn,
                onBack = onBack,
                onForgotten = onForgotten,
            )
        }
    }
}
