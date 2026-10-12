package com.homelab.household.app.screens.changepin

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
import com.homelab.household.app.resources.a11y_change_pin_saving
import com.homelab.household.app.resources.change_pin_again
import com.homelab.household.app.resources.change_pin_current
import com.homelab.household.app.resources.change_pin_mismatch
import com.homelab.household.app.resources.change_pin_new
import com.homelab.household.app.resources.change_pin_saving
import com.homelab.household.app.resources.change_pin_submit
import com.homelab.household.app.resources.change_pin_title
import com.homelab.household.app.resources.change_pin_wrong
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import org.jetbrains.compose.resources.getString

/** Everything the change-PIN screen says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class ChangePinRobot(
    private val test: ComposeUiTest,
) {
    suspend fun typesTheCurrentPin(pin: String) {
        test.onNodeWithContentDescription(getString(Res.string.change_pin_current)).performTextInput(pin)
    }

    suspend fun typesTheNewPin(pin: String) {
        test.onNodeWithContentDescription(getString(Res.string.change_pin_new)).performTextInput(pin)
    }

    suspend fun typesTheNewPinAgain(pin: String) {
        test.onNodeWithContentDescription(getString(Res.string.change_pin_again)).performTextInput(pin)
    }

    suspend fun tapsChange() {
        test.onNodeWithText(getString(Res.string.change_pin_submit)).performClick()
    }

    suspend fun seesTheTitle() = seesText(getString(Res.string.change_pin_title))

    /** The hub refused the current PIN, and says how many tries are left. */
    suspend fun seesTheCurrentPinWrong(attemptsLeft: Int) =
        seesText(getString(Res.string.change_pin_wrong, attemptsLeft))

    suspend fun seesTheNewPinsDiffer() = seesText(getString(Res.string.change_pin_mismatch))

    /**
     * Nothing is dimmed: the button keeps its colour, stays enabled, and a screen reader hears what
     * is happening rather than "Working".
     */
    suspend fun seesItSavingOnTheButton() {
        val saving = getString(Res.string.change_pin_saving)
        test.waitUntilExactlyOneExists(hasText(saving), timeoutMillis = WAIT_MILLIS)
        test
            .onNodeWithText(saving)
            .assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_change_pin_saving),
                ),
            )
        test.onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertIsDisplayed()
        test.onNodeWithText(getString(Res.string.change_pin_submit)).assertDoesNotExist()
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
suspend fun ComposeUiTest.onChangePin(block: suspend ChangePinRobot.() -> Unit) {
    ChangePinRobot(this).block()
}

/** The change-PIN screen over a hub that answers however the test says; [tokens] is the phone's session. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.changePinScreen(
    hub: FakeMembersHub,
    tokens: StoredSessionLocalDataSource = InMemorySessionStorage().apply { saveTokens("signed-in-token") },
    onChange: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine, sessionStorage = tokens) {
            ChangePinScreen(onBack = {}, onChange = onChange)
        }
    }
}
