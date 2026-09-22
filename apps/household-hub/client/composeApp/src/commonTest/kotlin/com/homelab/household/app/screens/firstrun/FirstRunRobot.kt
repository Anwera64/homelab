package com.homelab.household.app.screens.firstrun

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.first_run_colour_swatch
import com.homelab.household.app.resources.first_run_create
import com.homelab.household.app.resources.first_run_failed_already_set_up
import com.homelab.household.app.resources.first_run_failed_unreachable
import com.homelab.household.app.resources.first_run_name_missing
import com.homelab.household.app.resources.first_run_overline
import com.homelab.household.app.resources.first_run_pin_not_six_digits
import com.homelab.household.app.resources.first_run_sign_in_instead
import com.homelab.household.app.resources.first_run_title
import com.homelab.household.app.testing.TestApp
import com.homelab.household.presentation.firstrun.AvatarPalette
import org.jetbrains.compose.resources.getString

/**
 * Everything the first-run form says and does, named once as the resources the screen reads.
 * The form has two text fields, name then PIN, and is found by that order.
 */
@OptIn(ExperimentalTestApi::class)
class FirstRunRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTheForm() {
        seesText(getString(Res.string.first_run_title))
        seesText(getString(Res.string.first_run_create))
    }

    fun typesName(name: String) {
        test.onAllNodes(hasSetTextAction())[NAME_FIELD].performScrollTo().performTextInput(name)
    }

    fun typesPin(pin: String) {
        test.onAllNodes(hasSetTextAction())[PIN_FIELD].performScrollTo().performTextInput(pin)
    }

    /** [position] counts from 1, the way the swatch describes itself. */
    suspend fun picksColour(position: Int) {
        val description = getString(Res.string.first_run_colour_swatch, position, AvatarPalette.swatches.size)
        test.onNodeWithContentDescription(description).performScrollTo().performClick()
    }

    suspend fun tapsCreate() {
        test.onNodeWithText(getString(Res.string.first_run_create)).performClick()
    }

    suspend fun seesTheNameIsMissing() = seesText(getString(Res.string.first_run_name_missing))

    suspend fun seesThePinIsIncomplete() = seesText(getString(Res.string.first_run_pin_not_six_digits))

    suspend fun seesTheHubDidNotAnswer() = seesText(getString(Res.string.first_run_failed_unreachable))

    suspend fun seesTheHubIsAlreadySetUp() {
        seesText(getString(Res.string.first_run_failed_already_set_up))
        seesText(getString(Res.string.first_run_sign_in_instead))
    }

    suspend fun tapsSignInInstead() {
        test.onNodeWithText(getString(Res.string.first_run_sign_in_instead)).performClick()
    }

    fun stillSeesTheName(name: String) {
        test.onAllNodes(hasSetTextAction())[NAME_FIELD].assertTextContains(name)
    }

    /** Taps the name field itself, the way a person opening the form would. */
    fun focusesTheName() {
        test.onAllNodes(hasSetTextAction())[NAME_FIELD].performClick()
    }

    fun seesTheNameIsFocused() {
        test.onAllNodes(hasSetTextAction())[NAME_FIELD].assertIsFocused()
    }

    fun seesTheNameIsNotFocused() {
        test.onAllNodes(hasSetTextAction())[NAME_FIELD].assertIsNotFocused()
    }

    /**
     * Taps the overline copy above the fields: not a field, not a button, not the colour picker —
     * the "form background" iOS offers no keyboard-return-key path off of.
     */
    suspend fun tapsTheFormBackground() {
        test.onNodeWithText(getString(Res.string.first_run_overline)).performTouchInput { click() }
    }

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val NAME_FIELD = 0
        const val PIN_FIELD = 1
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onFirstRun(block: suspend FirstRunRobot.() -> Unit) {
    FirstRunRobot(this).block()
}

/** The first-run screen over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.firstRunScreen(
    hub: FakeFirstRunHub,
    onCreated: () -> Unit = {},
    onSignIn: () -> Unit = {},
) {
    setContent {
        TestApp(hub.engine) {
            FirstRunScreen(onCreated = onCreated, onSignIn = onSignIn)
        }
    }
}
