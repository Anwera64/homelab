package com.homelab.household.app.screens.invitecode

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.invite_code_title
import com.homelab.household.app.testing.FakeJoinHub
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.domain.model.InvitePreview
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun every_previewed_state_draws() {
        val states = InviteCodeUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    HearthTheme(darkTheme = false) {
                        InviteCodeContent(
                            state = state,
                            onCodeChange = {},
                            onContinue = {},
                            onPaste = {},
                            onBack = {}
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
