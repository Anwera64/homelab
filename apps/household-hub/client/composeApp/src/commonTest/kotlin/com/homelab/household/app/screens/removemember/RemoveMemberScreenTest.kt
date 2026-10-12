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
    fun typing_their_name_removes_them() {
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
    fun another_name_says_so_under_the_field() {
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
    fun removing_them_shows_on_the_button_and_names_them() =
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
    fun every_previewed_state_draws() {
        RemoveMemberUiStateProvider().values.forEach { state ->
            runComposeUiTest {
                removeMemberContent(state)

                onRemoveMember { seesTheTitleFor(state.member.name) }
            }
        }
    }
}
