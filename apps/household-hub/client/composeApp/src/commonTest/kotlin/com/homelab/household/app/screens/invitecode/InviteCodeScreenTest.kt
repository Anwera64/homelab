package com.homelab.household.app.screens.invitecode

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeJoinHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.presentation.invitecode.InviteCodeStatus
import com.homelab.household.presentation.invitecode.InviteCodeUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Entering an invite code, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class InviteCodeScreenTest {
    @Test
    fun `GIVEN a hub that knows the code WHEN the code is typed and continue is tapped THEN the join screen opens with the invite details and the upper-cased code`() {
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
    fun `GIVEN a short code WHEN continue is tapped THEN the screen says the code is short`() {
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
    fun `GIVEN a hub that knows no such code WHEN the code is typed and continue is tapped THEN the screen says the code is not valid`() {
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
    fun `GIVEN a hub that has had enough guessing WHEN a code is typed and continue is tapped THEN the screen says how long to wait`() {
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
    fun `GIVEN an unreachable hub WHEN a code is typed and continue is tapped THEN the screen says the hub did not answer`() {
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
    fun `GIVEN the invite code screen WHEN back is tapped THEN the screen hands back`() {
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
    fun `GIVEN a code being checked WHEN the screen is shown THEN the button says it is checking with a progress bar and continue is gone`() =
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

            onInviteCode {
                seesTheButtonCheckingTheCode()
                seesTheProgressBar()
                doesNotSeeContinue()
            }
        }

    /**
     * The slow line sits under the button, so the button now has a `Column` between it and the
     * screen. A wrapper that keeps the caller's width for itself is exactly how every full-width
     * button in the app came to be a full-width box around a button hugging its label — invisible
     * to tests that assert semantics, colour and height and never once a width.
     */
    @Test
    fun `GIVEN a typed code WHEN the screen is shown THEN the continue button fills the screen width`() =
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

            val (screen, button) = onInviteCode { screenWidth() to continueButtonWidth() }

            assertTrue(
                button > screen / 2,
                "the button was told to fill its width but measured $button inside a $screen screen",
            )
        }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the title is there`() {
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

                onInviteCode { seesTheTitle() }
            }
        }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
