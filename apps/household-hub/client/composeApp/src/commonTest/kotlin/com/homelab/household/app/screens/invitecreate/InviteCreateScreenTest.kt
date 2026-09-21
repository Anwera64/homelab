package com.homelab.household.app.screens.invitecreate

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
import com.homelab.household.app.components.HearthProgressBarTag
import com.homelab.household.app.components.SkeletonGroupTag
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_invite_create_making
import com.homelab.household.app.resources.a11y_invite_create_remaking
import com.homelab.household.app.resources.invite_create_making
import com.homelab.household.app.resources.invite_create_making_new
import com.homelab.household.app.resources.invite_create_name_label
import com.homelab.household.app.resources.invite_create_name_missing
import com.homelab.household.app.resources.invite_create_new_code
import com.homelab.household.app.resources.invite_create_title
import com.homelab.household.app.resources.join_name_taken
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemoryTokenStorage
import com.homelab.household.domain.model.Invite
import com.homelab.household.presentation.invitecreate.InviteCreateStatus
import com.homelab.household.presentation.invitecreate.InviteCreateUiState
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.getString

/** Making an invite code, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class InviteCreateScreenTest {

    private val wait = 5_000L

    /** A phone with somebody signed in on it: these screens are all behind a token. */
    private fun signedIn() = InMemoryTokenStorage().apply { saveTokens("signed-in-token") }

    @Test
    fun a_name_and_a_tap_produce_a_code_to_read_out() {
        val hub = FakeMembersHub()
        hub.makesInvites(code = "K7M2QP")

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { InviteCreateScreen(onBack = {}) } }

            onNodeWithContentDescription(getString(Res.string.invite_create_name_label)).performTextInput("Liam")
            onNodeWithText(getString(Res.string.invite_create_new_code)).performClick()

            waitUntilExactlyOneExists(hasText("K7M2QP"), timeoutMillis = wait)
        }

        assertTrue(hub.requests.any { it.contains(""""invited_name":"Liam"""") }, hub.requests.toString())
    }

    @Test
    fun a_blank_name_says_so_under_the_field() {
        val hub = FakeMembersHub()

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { InviteCreateScreen(onBack = {}) } }

            onNodeWithText(getString(Res.string.invite_create_new_code)).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.invite_create_name_missing)), timeoutMillis = wait)
        }
    }

    @Test
    fun a_name_the_household_already_has_says_so() {
        val hub = FakeMembersHub()
        hub.saysTheNameIsTaken()

        runScreenTest {
            setContent { TestApp(hub.engine, tokenStorage = signedIn()) { InviteCreateScreen(onBack = {}) } }

            onNodeWithContentDescription(getString(Res.string.invite_create_name_label)).performTextInput("Emma")
            onNodeWithText(getString(Res.string.invite_create_new_code)).performClick()

            waitUntilExactlyOneExists(hasText(getString(Res.string.join_name_taken)), timeoutMillis = wait)
        }
    }

    /**
     * Asking the hub for a code, with nothing on screen yet to replace.
     * Nothing is dimmed: the button keeps its colour, stays enabled, and a screen reader hears what
     * is happening rather than "Working" (design notes §2, §6.21).
     */
    @Test
    fun making_the_first_code_shows_on_the_button() = runComposeUiTest {
        setContent {
            StillTheme {
                InviteCreateContent(
                    state = InviteCreateUiState(name = "Liam", status = InviteCreateStatus.Creating),
                    onNameChange = {},
                    onAdminChange = {},
                    onCreate = {},
                    onNewCode = {},
                    onCopy = {},
                    onBack = {}
                )
            }
        }

        onNodeWithText(getString(Res.string.invite_create_making))
            .assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    getString(Res.string.a11y_invite_create_making)
                )
            )
        onNodeWithTag(HearthProgressBarTag).assertIsDisplayed()
        onNodeWithText(getString(Res.string.invite_create_new_code)).assertDoesNotExist()
    }

    /**
     * Replacing a code already on screen. The button that asked carries the bar, and it announces
     * itself differently — this is a *new* code, and the one on screen is about to stop working.
     */
    @Test
    fun making_a_second_code_announces_that_it_is_a_new_one() = runComposeUiTest {
        val invite = Invite(code = "K7M2QP", invitedName = "Liam", isAdmin = false, expiresInSeconds = 892)

        setContent {
            StillTheme {
                InviteCreateContent(
                    state = InviteCreateUiState(
                        name = "Liam",
                        invite = invite,
                        secondsLeft = 892,
                        status = InviteCreateStatus.Creating
                    ),
                    onNameChange = {},
                    onAdminChange = {},
                    onCreate = {},
                    onNewCode = {},
                    onCopy = {},
                    onBack = {}
                )
            }
        }

        onNodeWithText(getString(Res.string.invite_create_making)).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                getString(Res.string.a11y_invite_create_remaking)
            )
        )

        // The old code must go. Leaving it on screen invites the admin to read out six characters
        // that are being replaced, which is worse than showing nothing (§6.21, pattern 4).
        onNodeWithText("K7M2QP").assertDoesNotExist()
        onNodeWithTag(SkeletonGroupTag).assertIsDisplayed()
        onNodeWithText(getString(Res.string.invite_create_making_new)).assertIsDisplayed()
    }

    @Test
    fun every_previewed_state_draws() {
        val states = InviteCreateUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        InviteCreateContent(
                            state = state,
                            onNameChange = {},
                            onAdminChange = {},
                            onCreate = {},
                            onNewCode = {},
                            onCopy = {},
                            onBack = {}
                        )
                    }
                }

                onNodeWithText(getString(Res.string.invite_create_title)).assertIsDisplayed()
            }
        }
    }
}
