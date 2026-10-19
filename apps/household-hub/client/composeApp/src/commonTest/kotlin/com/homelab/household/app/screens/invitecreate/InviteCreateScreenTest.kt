package com.homelab.household.app.screens.invitecreate

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.Invite
import com.homelab.household.presentation.invitecreate.InviteCreateStatus
import com.homelab.household.presentation.invitecreate.InviteCreateUiState
import kotlin.test.Test
import kotlin.test.assertTrue

/** Making an invite code, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class InviteCreateScreenTest {
    @Test
    fun `GIVEN a hub that makes invites WHEN a name is typed and new code is tapped THEN the code shows and the name reached the hub`() {
        val hub = FakeMembersHub()
        hub.makesInvites(code = "K7M2QP")

        runScreenTest {
            inviteCreateScreen(hub)

            onInviteCreate {
                typesTheName("Liam")
                tapsNewCode()
                seesTheCode("K7M2QP")
            }
        }

        assertTrue(hub.requests.any { it.contains(""""invited_name":"Liam"""") }, hub.requests.toString())
    }

    @Test
    fun `GIVEN a blank name WHEN new code is tapped THEN the name field says it is missing`() {
        val hub = FakeMembersHub()

        runScreenTest {
            inviteCreateScreen(hub)

            onInviteCreate {
                tapsNewCode()
                seesTheNameIsMissing()
            }
        }
    }

    @Test
    fun `GIVEN a hub that says the name is taken WHEN the name is typed and new code is tapped THEN the screen says the name is taken`() {
        val hub = FakeMembersHub()
        hub.saysTheNameIsTaken()

        runScreenTest {
            inviteCreateScreen(hub)

            onInviteCreate {
                typesTheName("Emma")
                tapsNewCode()
                seesTheNameIsTaken()
            }
        }
    }

    /**
     * Asking the hub for a code, with nothing on screen yet to replace.
     * Nothing is dimmed: the button keeps its colour, stays enabled, and a screen reader hears what
     * is happening rather than "Working" (design notes §2, §6.21).
     */
    @Test
    fun `GIVEN the first code being made WHEN the screen is shown THEN the button says it is making the first code and new code is gone`() =
        runComposeUiTest {
            inviteCreateContent(InviteCreateUiState(name = "Liam", status = InviteCreateStatus.Creating))

            onInviteCreate {
                seesTheButtonMakingTheFirstCode()
                doesNotSeeNewCode()
            }
        }

    /**
     * Replacing a code already on screen. The button that asked carries the bar, and it announces
     * itself differently — this is a *new* code, and the one on screen is about to stop working.
     */
    @Test
    fun `GIVEN a code on screen and a new one being made WHEN the screen is shown THEN the button says it is making a new code and the old code is gone`() =
        runComposeUiTest {
            val invite = Invite(code = "K7M2QP", invitedName = "Liam", isAdmin = false, expiresInSeconds = 892)

            inviteCreateContent(
                InviteCreateUiState(
                    name = "Liam",
                    invite = invite,
                    secondsLeft = 892,
                    status = InviteCreateStatus.Creating,
                ),
            )

            onInviteCreate {
                seesTheButtonMakingANewCode()

                // The old code must go. Leaving it on screen invites the admin to read out six characters
                // that are being replaced, which is worse than showing nothing (§6.21, pattern 4).
                doesNotSeeTheCode("K7M2QP")
                seesTheCodePlaceholder()
                seesTheNewCodeIsComing()
            }
        }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the title is there`() {
        val states = InviteCreateUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                inviteCreateContent(state)

                onInviteCreate { seesTheTitle() }
            }
        }
    }
}
