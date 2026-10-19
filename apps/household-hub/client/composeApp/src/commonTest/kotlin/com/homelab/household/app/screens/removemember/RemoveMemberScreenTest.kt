package com.homelab.household.app.screens.removemember

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.removemember.RemoveMemberStatus
import com.homelab.household.presentation.removemember.RemoveMemberUiState
import kotlin.test.Test

/** Removing a member, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class RemoveMemberScreenTest {
    private val liam = Member(id = "liam", name = "Liam", avatarColor = "#C05638")
    private val wait = 5_000L

    @Test
    fun `GIVEN a hub that removes members WHEN the member's name is typed and remove is tapped THEN the member is removed`() {
        val hub = FakeMembersHub()
        hub.doesIt()
        var removed = 0

        runScreenTest {
            removeMemberScreen(hub, liam, onRemove = { removed++ })

            onRemoveMember {
                typesTheName(member = "Liam", typed = "Liam")
                tapsRemove("Liam")
            }

            waitUntil(timeoutMillis = wait) { removed == 1 }
        }
    }

    @Test
    fun `GIVEN a different name typed WHEN remove is tapped THEN the field says the name does not match`() {
        val hub = FakeMembersHub()

        runScreenTest {
            removeMemberScreen(hub, liam)

            onRemoveMember {
                typesTheName(member = "Liam", typed = "Li")
                tapsRemove("Liam")
                seesTheNameDoesNotMatch()
            }
        }
    }

    /**
     * Removing cannot be undone, so the wait is the one moment it is still worth showing plainly.
     * The announcement names the person, because "Removing" alone is the wrong thing to be vague
     * about.
     */
    @Test
    fun `GIVEN a removal in progress WHEN the screen is shown THEN the button says it is removing the member by name and remove is gone`() =
        runComposeUiTest {
            removeMemberContent(
                RemoveMemberUiState(
                    member = liam,
                    typedName = "Liam",
                    status = RemoveMemberStatus.Removing,
                ),
            )

            onRemoveMember {
                seesTheButtonRemoving("Liam")
                doesNotSeeRemove("Liam")
            }
        }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the title names the member`() {
        RemoveMemberUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                removeMemberContent(state)

                onRemoveMember { seesTheTitleFor(state.member.name) }
            }
        }
    }
}
