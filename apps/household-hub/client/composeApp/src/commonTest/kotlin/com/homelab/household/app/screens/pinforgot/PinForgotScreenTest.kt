package com.homelab.household.app.screens.pinforgot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.components.SkeletonGroupTag
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.forgot_alone_title
import com.homelab.household.app.resources.forgot_ask
import com.homelab.household.app.resources.forgot_have_code
import com.homelab.household.app.resources.forgot_title
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemoryTokenStorage
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.pinforgot.PinForgotStatus
import com.homelab.household.presentation.pinforgot.PinForgotUiState
import kotlin.test.Test
import org.jetbrains.compose.resources.getString

/** A forgotten PIN, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class PinForgotScreenTest {

    private val emma = Member(id = "emma", name = "Emma Larsson", avatarColor = "#3C6E4E")
    private val wait = 5_000L

    /** A phone with somebody signed in on it: these screens are all behind a token. */
    private fun signedIn() = InMemoryTokenStorage().apply { saveTokens("signed-in-token") }

    @Test
    fun it_names_the_housemate_who_can_vouch() {
        val hub = FakeMembersHub()

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { PinForgotScreen(member = emma, onBack = {}, onHaveCode = {}) } }

            waitUntilExactlyOneExists(hasText(getString(Res.string.forgot_ask, "Liam")), timeoutMillis = wait)
            onNodeWithText(getString(Res.string.forgot_alone_title)).assertIsDisplayed()
        }
    }

    @Test
    fun having_a_code_already_goes_straight_on() {
        val hub = FakeMembersHub()
        var code = 0

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { PinForgotScreen(member = emma, onBack = {}, onHaveCode = { code++ }) } }

            onNodeWithText(getString(Res.string.forgot_have_code)).performClick()
            waitUntil(timeoutMillis = wait) { code == 1 }
        }
    }

    @Test
    fun arriving_stands_blocks_where_the_housemate_goes_and_keeps_the_fallback_on_screen() {
        runComposeUiTest {
            setContent {
                StillTheme {
                    PinForgotContent(
                        state = PinForgotUiState(member = emma, others = emptyList(), status = PinForgotStatus.Loading),
                        onHaveCode = {},
                        onBack = {}
                    )
                }
            }

            onNodeWithTag(SkeletonGroupTag).assertIsDisplayed()
            // The hub's own reset is the backstop, and it does not depend on the hub answering.
            onNodeWithText(getString(Res.string.forgot_alone_title)).assertIsDisplayed()
        }
    }

    @Test
    fun every_previewed_state_draws() {
        val states = PinForgotUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        PinForgotContent(state = state, onHaveCode = {}, onBack = {})
                    }
                }

                onNodeWithText(getString(Res.string.forgot_title)).assertIsDisplayed()
            }
        }
    }
}
