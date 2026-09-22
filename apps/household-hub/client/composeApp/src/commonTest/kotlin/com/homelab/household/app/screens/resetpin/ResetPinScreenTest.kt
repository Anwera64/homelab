package com.homelab.household.app.screens.resetpin

import androidx.compose.ui.semantics.SemanticsProperties
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
import androidx.compose.ui.test.runComposeUiTest
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
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.presentation.resetpin.NewPinStatus
import com.homelab.household.presentation.resetpin.NewPinUiState
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/** Redeeming a reset code, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class ResetPinScreenTest {
    private val wait = 5_000L

    @Test
    fun the_code_is_carried_to_the_new_pin_screen() {
        val hub = FakeJoinHub()
        val codes = mutableListOf<String>()

        runScreenTest {
            setContent { TestApp(hub.engine) { ResetCodeScreen(onBack = {}, onCode = { codes += it }) } }

            onNodeWithContentDescription(getString(Res.string.reset_code_field)).performTextInput("p4xn7t")
            onNodeWithText(getString(Res.string.invite_code_continue)).performClick()

            waitUntil(timeoutMillis = wait) { codes.size == 1 }
        }

        assertEquals("P4XN7T", codes.single())
    }

    @Test
    fun the_new_pin_signs_the_member_in() {
        val hub = FakeJoinHub()
        hub.letsThemJoinAs(memberId = "emma", fullName = "Emma")
        val tokens = InMemorySessionStorage()
        var signedIn = 0

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = tokens) {
                    NewPinScreen(code = "P4XN7T", onSignedIn = { signedIn++ })
                }
            }

            onNodeWithContentDescription(getString(Res.string.new_pin_label)).performTextInput("864209")
            onNodeWithContentDescription(getString(Res.string.new_pin_again)).performTextInput("864209")
            onNodeWithText(getString(Res.string.new_pin_submit)).performClick()

            waitUntil(timeoutMillis = wait) { signedIn == 1 }
        }

        assertEquals("joined-token", tokens.getAccessToken())
    }

    @Test
    fun a_code_that_has_gone_says_so() {
        val hub = FakeJoinHub()
        hub.saysTheCodeHasGone()

        runScreenTest {
            setContent { TestApp(hub.engine) { NewPinScreen(code = "P4XN7T", onSignedIn = {}) } }

            onNodeWithContentDescription(getString(Res.string.new_pin_label)).performTextInput("864209")
            onNodeWithContentDescription(getString(Res.string.new_pin_again)).performTextInput("864209")
            onNodeWithText(getString(Res.string.new_pin_submit)).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.reset_code_invalid)), timeoutMillis = wait)
        }
    }

    @Test
    fun two_different_pins_say_so_under_the_second_field() {
        val hub = FakeJoinHub()

        runScreenTest {
            setContent { TestApp(hub.engine) { NewPinScreen(code = "P4XN7T", onSignedIn = {}) } }

            onNodeWithContentDescription(getString(Res.string.new_pin_label)).performTextInput("864209")
            onNodeWithContentDescription(getString(Res.string.new_pin_again)).performTextInput("864200")
            onNodeWithText(getString(Res.string.new_pin_submit)).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.change_pin_mismatch)), timeoutMillis = wait)
        }
    }

    /**
     * The end of a PIN recovery: the code has been accepted and the new PIN is going to the hub.
     * Nothing is dimmed, and the button says which PIN is being set.
     */
    @Test
    fun setting_the_new_pin_shows_on_the_button() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    NewPinContent(
                        state = NewPinUiState(pin = "864209", again = "864209", status = NewPinStatus.Setting),
                        onPinChange = {},
                        onAgainChange = {},
                        onSetPin = {},
                    )
                }
            }

            onNodeWithText(getString(Res.string.new_pin_setting))
                .assertIsEnabled()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        getString(Res.string.a11y_new_pin_setting),
                    ),
                )
            onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertIsDisplayed()
            onNodeWithText(getString(Res.string.new_pin_submit)).assertDoesNotExist()
        }

    @Test
    fun every_previewed_state_draws() {
        ResetCodeUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        ResetCodeContent(state = state, onCodeChange = {}, onContinue = {}, onBack = {})
                    }
                }

                onNodeWithText(getString(Res.string.reset_code_title)).assertIsDisplayed()
            }
        }

        NewPinUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        NewPinContent(state = state, onPinChange = {}, onAgainChange = {}, onSetPin = {})
                    }
                }

                onNodeWithText(getString(Res.string.new_pin_title)).assertIsDisplayed()
            }
        }
    }
}
