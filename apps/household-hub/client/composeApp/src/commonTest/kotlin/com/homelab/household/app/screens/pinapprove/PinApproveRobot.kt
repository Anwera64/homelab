package com.homelab.household.app.screens.pinapprove

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
import com.homelab.household.app.resources.a11y_approve_checking
import com.homelab.household.app.resources.approve_checking
import com.homelab.household.app.resources.approve_generate
import com.homelab.household.app.resources.approve_pin_label
import com.homelab.household.app.resources.approve_title
import com.homelab.household.app.resources.approve_wrong_pin
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.pinapprove.PinApproveUiState
import org.jetbrains.compose.resources.getString

/** Everything the vouching screen says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class PinApproveRobot(
    private val test: ComposeUiTest,
) {
    /** The title names the housemate being vouched for. */
    suspend fun seesTheTitleFor(name: String) = seesText(getString(Res.string.approve_title, name))

    suspend fun typesYourPin(pin: String) {
        test.onNodeWithContentDescription(getString(Res.string.approve_pin_label)).performTextInput(pin)
    }

    suspend fun tapsGenerate() {
        test.onNodeWithText(getString(Res.string.approve_generate)).performClick()
    }

    /** The code to read out, as the hub made it. */
    fun seesTheCode(code: String) = seesText(code)

    suspend fun seesTriesLeft(attemptsLeft: Int) = seesText(getString(Res.string.approve_wrong_pin, attemptsLeft))

    /** The button says the PIN is being checked, stays enabled, and announces what is happening. */
    suspend fun seesTheButtonChecking() {
        test.onNodeWithText(getString(Res.string.approve_checking))
            .assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_approve_checking),
                ),
            )
        test.onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertIsDisplayed()
    }

    suspend fun doesNotSeeGenerate() {
        test.onNodeWithText(getString(Res.string.approve_generate)).assertDoesNotExist()
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
suspend fun ComposeUiTest.onPinApprove(block: suspend PinApproveRobot.() -> Unit) {
    PinApproveRobot(this).block()
}

/** The vouching screen for [member], signed in, over a hub that answers however the test says. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.pinApproveScreen(
    hub: FakeMembersHub,
    member: Member,
) {
    setContent {
        TestApp(hub.engine, sessionStorage = InMemorySessionStorage().apply { saveTokens("signed-in-token") }) {
            PinApproveScreen(member = member, onBack = {})
        }
    }
}

/** The vouching screen's content drawn for [state]. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.pinApproveContent(state: PinApproveUiState) {
    setContent {
        StillTheme {
            PinApproveContent(state = state, onPinChange = {}, onApprove = {}, onBack = {})
        }
    }
}
