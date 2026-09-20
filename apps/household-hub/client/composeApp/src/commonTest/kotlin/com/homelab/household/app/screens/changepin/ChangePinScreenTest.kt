package com.homelab.household.app.screens.changepin

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.change_pin_again
import com.homelab.household.app.resources.change_pin_current
import com.homelab.household.app.resources.change_pin_mismatch
import com.homelab.household.app.resources.change_pin_new
import com.homelab.household.app.resources.change_pin_submit
import com.homelab.household.app.resources.change_pin_title
import com.homelab.household.app.resources.change_pin_wrong
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.data.datasource.local.InMemoryTokenStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.getString

/** Changing a PIN, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class ChangePinScreenTest {

    private val wait = 5_000L

    /** A phone with somebody signed in on it: these screens are all behind a token. */
    private fun signedIn() = InMemoryTokenStorage().apply { saveTokens("signed-in-token") }

    @Test
    fun the_new_pin_is_taken_and_this_phone_keeps_its_place() {
        val hub = FakeMembersHub()
        hub.takesTheChange()
        val tokens = InMemoryTokenStorage().apply { saveTokens("old-token") }
        var changed = 0

        runScreenTest {
            setContent {
                TestApp(hub.engine, tokenStorage = tokens) {
                    ChangePinScreen(onBack = {}, onChanged = { changed++ })
                }
            }

            onNodeWithContentDescription(getString(Res.string.change_pin_current)).performTextInput("135790")
            onNodeWithContentDescription(getString(Res.string.change_pin_new)).performTextInput("864209")
            onNodeWithContentDescription(getString(Res.string.change_pin_again)).performTextInput("864209")
            onNodeWithText(getString(Res.string.change_pin_submit)).performClick()

            waitUntil(timeoutMillis = wait) { changed == 1 }
        }

        assertEquals("fresh-token", tokens.getAccessToken())
    }

    @Test
    fun a_wrong_current_pin_lands_under_that_field() {
        val hub = FakeMembersHub()
        hub.refusesThePin(attemptsLeft = 4)

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { ChangePinScreen(onBack = {}, onChanged = {}) } }

            onNodeWithContentDescription(getString(Res.string.change_pin_current)).performTextInput("000000")
            onNodeWithContentDescription(getString(Res.string.change_pin_new)).performTextInput("864209")
            onNodeWithContentDescription(getString(Res.string.change_pin_again)).performTextInput("864209")
            onNodeWithText(getString(Res.string.change_pin_submit)).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.change_pin_wrong, 4)), timeoutMillis = wait)
        }
    }

    @Test
    fun two_different_new_pins_say_so_under_the_second_field() {
        val hub = FakeMembersHub()

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { ChangePinScreen(onBack = {}, onChanged = {}) } }

            onNodeWithContentDescription(getString(Res.string.change_pin_current)).performTextInput("135790")
            onNodeWithContentDescription(getString(Res.string.change_pin_new)).performTextInput("864209")
            onNodeWithContentDescription(getString(Res.string.change_pin_again)).performTextInput("864200")
            onNodeWithText(getString(Res.string.change_pin_submit)).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.change_pin_mismatch)), timeoutMillis = wait)
        }
    }

    @Test
    fun every_previewed_state_draws() {
        val states = ChangePinUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    HearthTheme(darkTheme = false) {
                        ChangePinContent(
                            state = state,
                            onCurrentChange = {},
                            onNewChange = {},
                            onAgainChange = {},
                            onChange = {},
                            onBack = {}
                        )
                    }
                }

                onNodeWithText(getString(Res.string.change_pin_title)).assertIsDisplayed()
            }
        }
    }
}
