package com.homelab.household.app.screens.invitecode

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.width
import com.homelab.household.app.components.HEARTH_PROGRESS_BAR_TAG
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.a11y_invite_code_checking
import com.homelab.household.app.resources.invite_code_checking
import com.homelab.household.app.resources.invite_code_continue
import com.homelab.household.app.resources.invite_code_title
import com.homelab.household.app.testing.FakeJoinHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.presentation.invitecode.InviteCodeStatus
import com.homelab.household.presentation.invitecode.InviteCodeUiState
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Entering an invite code, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class InviteCodeScreenTest {
    @Test
    fun a_good_code_opens_the_join_screen() {
        val hub = FakeJoinHub()
        hub.knowsTheCodeOf(invitedName = "Liam", inviterName = "Emma")
        val opened = mutableListOf<Pair<InvitePreview, String>>()

        runScreenTest {
            inviteCodeScreen(hub, onInvite = { preview, code -> opened += preview to code })

            onInviteCode {
                seesTheCodeScreen()
                types("k7m2qp")
                tapsContinue()
            }
            waitUntil(timeoutMillis = WAIT_MILLIS) { opened.size == 1 }
        }

        assertEquals("Liam", opened.single().first.invitedName)
        assertEquals("Emma", opened.single().first.inviterName)
        assertEquals("K7M2QP", opened.single().second)
    }

    @Test
    fun a_short_code_says_so_rather_than_blocking_the_button() {
        val hub = FakeJoinHub()

        runScreenTest {
            inviteCodeScreen(hub)

            onInviteCode {
                types("k7m")
                tapsContinue()
                seesThatTheCodeIsShort()
            }
        }
    }

    @Test
    fun a_code_the_hub_does_not_know_says_so() {
        val hub = FakeJoinHub()
        hub.knowsNoSuchCode()

        runScreenTest {
            inviteCodeScreen(hub)

            onInviteCode {
                types("zzzzzz")
                tapsContinue()
                seesThatTheCodeIsNotValid()
            }
        }
    }

    @Test
    fun too_much_guessing_says_how_long_to_wait() {
        val hub = FakeJoinHub()
        hub.hasHadEnoughGuessing(seconds = 30)

        runScreenTest {
            inviteCodeScreen(hub)

            onInviteCode {
                types("zzzzzz")
                tapsContinue()
                seesTheWaitOf(30)
            }
        }
    }

    @Test
    fun an_unreachable_hub_says_so() {
        val hub = FakeJoinHub()
        hub.isOffline()

        runScreenTest {
            inviteCodeScreen(hub)

            onInviteCode {
                types("k7m2qp")
                tapsContinue()
                seesTheHubDidNotAnswer()
            }
        }
    }

    @Test
    fun back_returns_to_who_is_here() {
        val hub = FakeJoinHub()
        var back = 0

        runScreenTest {
            inviteCodeScreen(hub, onBack = { back++ })

            onInviteCode { tapsBack() }
            waitUntil(timeoutMillis = WAIT_MILLIS) { back == 1 }
        }
    }

    /**
     * The tapped button carries the wait: its label becomes the verb in progress, a bar runs along
     * its bottom edge, and a screen reader hears what is being checked rather than "Working".
     * Nothing is dimmed — the button stays enabled and at full colour (design notes §2).
     */
    @Test
    fun checking_the_code_shows_on_the_button_that_asked() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    InviteCodeContent(
                        state = InviteCodeUiState(code = "K7M2QP", status = InviteCodeStatus.Checking),
                        onCodeChange = {},
                        onContinue = {},
                        onPaste = {},
                        onBack = {},
                    )
                }
            }

            onNodeWithText(getString(Res.string.invite_code_checking))
                .assertIsEnabled()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        getString(Res.string.a11y_invite_code_checking),
                    ),
                )
            onNodeWithTag(HEARTH_PROGRESS_BAR_TAG).assertIsDisplayed()
            onNodeWithText(getString(Res.string.invite_code_continue)).assertDoesNotExist()
        }

    /**
     * The slow line sits under the button, so the button now has a `Column` between it and the
     * screen. A wrapper that keeps the caller's width for itself is exactly how every full-width
     * button in the app came to be a full-width box around a button hugging its label — invisible
     * to tests that assert semantics, colour and height and never once a width.
     */
    @Test
    fun the_continue_button_still_fills_the_screen_under_its_wrapper() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    InviteCodeContent(
                        state = InviteCodeUiState(code = "K7M2QP"),
                        onCodeChange = {},
                        onContinue = {},
                        onPaste = {},
                        onBack = {},
                    )
                }
            }

            val screen = onRoot().getUnclippedBoundsInRoot().width
            val button =
                onNodeWithText(getString(Res.string.invite_code_continue))
                    .getUnclippedBoundsInRoot()
                    .width

            assertTrue(
                button > screen / 2,
                "the button was told to fill its width but measured $button inside a $screen screen",
            )
        }

    @Test
    fun every_previewed_state_draws() {
        val states = InviteCodeUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        InviteCodeContent(
                            state = state,
                            onCodeChange = {},
                            onContinue = {},
                            onPaste = {},
                            onBack = {},
                        )
                    }
                }

                onNodeWithText(getString(Res.string.invite_code_title)).assertIsDisplayed()
            }
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
