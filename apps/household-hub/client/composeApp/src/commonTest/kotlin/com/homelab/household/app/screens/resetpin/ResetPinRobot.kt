package com.homelab.household.app.screens.resetpin

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.components.HEARTH_PROGRESS_BAR_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_new_pin_setting
import com.homelab.household.app.resources.change_pin_mismatch
import com.homelab.household.app.resources.invite_code_continue
import com.homelab.household.app.resources.new_pin_again
import com.homelab.household.app.resources.new_pin_label
import com.homelab.household.app.resources.new_pin_setting
import com.homelab.household.app.resources.new_pin_submit
import com.homelab.household.app.resources.new_pin_title
import com.homelab.household.app.resources.reset_code_field
import com.homelab.household.app.resources.reset_code_invalid
import com.homelab.household.app.resources.reset_code_title
import com.homelab.household.app.testing.FakeJoinHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import org.jetbrains.compose.resources.getString

/** Everything the PIN-recovery screens say and do, named as the resources they read. */
@OptIn(ExperimentalTestApi::class)
class ResetPinRobot(
    private val test: ComposeUiTest,
) {
    suspend fun typesTheResetCode(code: String) {
        test.onNodeWithContentDescription(getString(Res.string.reset_code_field)).performTextInput(code)
    }

    suspend fun tapsContinue() {
        test.onNodeWithText(getString(Res.string.invite_code_continue)).performClick()
    }

    suspend fun typesTheNewPin(pin: String) {
        test.onNodeWithContentDescription(getString(Res.string.new_pin_label)).performTextInput(pin)
    }

    suspend fun typesTheNewPinAgain(pin: String) {
        test.onNodeWithContentDescription(getString(Res.string.new_pin_again)).performTextInput(pin)
    }

    suspend fun tapsSetThePin() {
        test.onNodeWithText(getString(Res.string.new_pin_submit)).performClick()
    }

    suspend fun seesTheResetCodeTitle() = seesText(getString(Res.string.reset_code_title))

    suspend fun seesTheNewPinTitle() = seesText(getString(Res.string.new_pin_title))

    suspend fun seesTheCodeHasGone() = seesText(getString(Res.string.reset_code_invalid))

    suspend fun seesThePinsDiffer() = seesText(getString(Res.string.change_pin_mismatch))

    /** Nothing is dimmed, and the button says which PIN is being set. */
    suspend fun seesItSettingOnTheButton() {
        val setting = getString(Res.string.new_pin_setting)
        test.waitUntilExactlyOneExists(hasText(setting), timeoutMillis = WAIT_MILLIS)
        test
            .onNodeWithText(setting)
            .assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_new_pin_setting),
                ),
            )
        test.onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertIsDisplayed()
        test.onNodeWithText(getString(Res.string.new_pin_submit)).assertDoesNotExist()
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
suspend fun ComposeUiTest.onResetPin(block: suspend ResetPinRobot.() -> Unit) {
    ResetPinRobot(this).block()
}

/** The reset-code screen over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.resetCodeScreen(
    hub: FakeJoinHub,
    onCode: (String) -> Unit = {},
) {
    setContent { TestApp(hub.engine) { ResetCodeScreen(onBack = {}, onCode = onCode) } }
}

/** The new-PIN screen for [code]; [tokens] is the phone's session, which a successful reset signs in. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.newPinScreen(
    hub: FakeJoinHub,
    code: String,
    tokens: StoredSessionLocalDataSource = InMemorySessionStorage(),
    onSignedIn: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine, sessionStorage = tokens) {
            NewPinScreen(code = code, onSignedIn = onSignedIn)
        }
    }
}
