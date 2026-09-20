package com.homelab.household.app.screens.pinapprove

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
import com.homelab.household.app.resources.approve_generate
import com.homelab.household.app.resources.approve_pin_label
import com.homelab.household.app.resources.approve_title
import com.homelab.household.app.resources.approve_wrong_pin
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.data.local.InMemoryTokenStorage
import com.homelab.household.domain.model.Member
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

/** Vouching for a housemate, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class PinApproveScreenTest {

    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")
    private val wait = 5_000L

    /** A phone with somebody signed in on it: these screens are all behind a token. */
    private fun signedIn() = InMemoryTokenStorage().apply { saveTokens("signed-in-token") }

    @Test
    fun your_own_pin_produces_the_code_to_read_out() {
        val hub = FakeMembersHub()
        hub.approvesResets(code = "P4XN7T")

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { PinApproveScreen(member = emma, onBack = {}) } }

            onNodeWithContentDescription(getString(Res.string.approve_pin_label)).performTextInput("246801")
            onNodeWithText(getString(Res.string.approve_generate)).performClick()

            waitUntilExactlyOneExists(hasText("P4XN7T"), timeoutMillis = wait)
        }
    }

    @Test
    fun a_wrong_pin_of_your_own_says_how_many_tries_are_left() {
        val hub = FakeMembersHub()
        hub.refusesThePin(attemptsLeft = 3)

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { PinApproveScreen(member = emma, onBack = {}) } }

            onNodeWithContentDescription(getString(Res.string.approve_pin_label)).performTextInput("000000")
            onNodeWithText(getString(Res.string.approve_generate)).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.approve_wrong_pin, 3)), timeoutMillis = wait)
        }
    }

    @Test
    fun every_previewed_state_draws() {
        PinApproveUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                setContent {
                    HearthTheme(darkTheme = false) {
                        PinApproveContent(state = state, onPinChange = {}, onApprove = {}, onBack = {})
                    }
                }

                onNodeWithText(getString(Res.string.approve_title, state.member.name)).assertIsDisplayed()
            }
        }
    }
}
