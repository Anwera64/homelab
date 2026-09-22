package com.homelab.household.app.screens.removemember

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
import com.homelab.household.app.resources.a11y_remove_removing
import com.homelab.household.app.resources.remove_confirm_label
import com.homelab.household.app.resources.remove_confirm_mismatch
import com.homelab.household.app.resources.remove_removing
import com.homelab.household.app.resources.remove_submit
import com.homelab.household.app.resources.remove_title
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.removemember.RemoveMemberStatus
import com.homelab.household.presentation.removemember.RemoveMemberUiState
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

/** Removing a member, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class RemoveMemberScreenTest {
    private val liam = Member(id = "liam", name = "Liam", avatarColor = "#C05638")
    private val wait = 5_000L

    /** A phone with somebody signed in on it: these screens are all behind a token. */
    private fun signedIn() = InMemorySessionStorage().apply { saveTokens("signed-in-token") }

    @Test
    fun typing_their_name_removes_them() {
        val hub = FakeMembersHub()
        hub.doesIt()
        var removed = 0

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn()) {
                    RemoveMemberScreen(member = liam, onBack = {}, onRemove = { removed++ })
                }
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
            setContent {
                TestApp(
                    hub.engine,
                    sessionStorage = signedIn(),
                ) { RemoveMemberScreen(member = liam, onBack = {}, onRemove = {}) }
            }

            onNodeWithContentDescription(getString(Res.string.remove_confirm_label, "Liam")).performTextInput("Li")
            onNodeWithText(getString(Res.string.remove_submit, "Liam")).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.remove_confirm_mismatch)), timeoutMillis = wait)
        }
    }

    /**
     * Removing cannot be undone, so the wait is the one moment it is still worth showing plainly.
     * The announcement names the person, because "Removing" alone is the wrong thing to be vague
     * about.
     */
    @Test
    fun removing_them_shows_on_the_button_and_names_them() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    RemoveMemberContent(
                        state =
                            RemoveMemberUiState(
                                member = liam,
                                typedName = "Liam",
                                status = RemoveMemberStatus.Removing,
                            ),
                        onNameChange = {},
                        onRemove = {},
                        onBack = {},
                    )
                }
            }

            onNodeWithText(getString(Res.string.remove_removing))
                .assertIsEnabled()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        getString(Res.string.a11y_remove_removing, "Liam"),
                    ),
                )
            onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertIsDisplayed()
            onNodeWithText(getString(Res.string.remove_submit, "Liam")).assertDoesNotExist()
        }

    @Test
    fun every_previewed_state_draws() {
        RemoveMemberUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        RemoveMemberContent(state = state, onNameChange = {}, onRemove = {}, onBack = {})
                    }
                }

                onNodeWithText(getString(Res.string.remove_title, state.member.name)).assertIsDisplayed()
            }
        }
    }
}
