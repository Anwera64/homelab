package com.homelab.household.app.screens.removemember

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
import com.homelab.household.app.resources.remove_confirm_label
import com.homelab.household.app.resources.remove_confirm_mismatch
import com.homelab.household.app.resources.remove_submit
import com.homelab.household.app.resources.remove_title
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.data.datasource.local.InMemoryTokenStorage
import com.homelab.household.domain.model.Member
import kotlin.test.Test
import org.jetbrains.compose.resources.getString

/** Removing a member, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class RemoveMemberScreenTest {

    private val liam = Member(id = "liam", name = "Liam", avatarColor = "#C05638")
    private val wait = 5_000L

    /** A phone with somebody signed in on it: these screens are all behind a token. */
    private fun signedIn() = InMemoryTokenStorage().apply { saveTokens("signed-in-token") }

    @Test
    fun typing_their_name_removes_them() {
        val hub = FakeMembersHub()
        hub.doesIt()
        var removed = 0

        runScreenTest {
            setContent {
                TestApp(hub.engine, tokenStorage = signedIn()) { RemoveMemberScreen(member = liam, onBack = {}, onRemoved = { removed++ }) }
            }

            onNodeWithContentDescription(getString(Res.string.remove_confirm_label, "Liam")).performTextInput("Liam")
            onNodeWithText(getString(Res.string.remove_submit, "Liam")).performClick()

            waitUntil(timeoutMillis = wait) { removed == 1 }
        }
    }

    @Test
    fun another_name_says_so_under_the_field() {
        val hub = FakeMembersHub()

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { RemoveMemberScreen(member = liam, onBack = {}, onRemoved = {}) } }

            onNodeWithContentDescription(getString(Res.string.remove_confirm_label, "Liam")).performTextInput("Li")
            onNodeWithText(getString(Res.string.remove_submit, "Liam")).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.remove_confirm_mismatch)), timeoutMillis = wait)
        }
    }

    @Test
    fun every_previewed_state_draws() {
        RemoveMemberUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                setContent {
                    HearthTheme(darkTheme = false) {
                        RemoveMemberContent(state = state, onNameChange = {}, onRemove = {}, onBack = {})
                    }
                }

                onNodeWithText(getString(Res.string.remove_title, state.member.name)).assertIsDisplayed()
            }
        }
    }
}
