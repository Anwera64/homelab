package com.homelab.household.app.screens.members

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.members.MembersStatus
import com.homelab.household.presentation.members.MembersUiState
import kotlin.test.Test
import kotlin.test.assertEquals

/** The members list, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class MembersScreenTest {
    private val wait = 5_000L

    @Test
    fun `GIVEN an admin on the members list WHEN reset PIN and then remove are tapped THEN everyone and invite are shown and both actions reach the callbacks`() {
        val hub = FakeMembersHub()
        val reset = mutableListOf<Member>()
        val removed = mutableListOf<Member>()

        runScreenTest {
            membersScreen(hub, onResetPin = { reset += it }, onRemove = { removed += it })

            onMembers {
                seesMember("Liam")
                seesMember("Emma Larsson")
                seesInvite()

                tapsResetPin()
            }
            waitUntil(timeoutMillis = wait) { reset.size == 1 }
            onMembers { tapsRemove() }
            waitUntil(timeoutMillis = wait) { removed.size == 1 }
        }

        assertEquals("Liam", reset.single().name)
        assertEquals("Liam", removed.single().name)
    }

    @Test
    fun `GIVEN a member who is not an admin WHEN the members list is shown THEN reset PIN shows but invite and remove do not`() {
        val hub = FakeMembersHub()
        hub.signedInAsAMember()

        runScreenTest {
            membersScreen(hub)

            onMembers {
                seesResetPin()
                doesNotSeeInvite()
                doesNotSeeRemove()
            }
        }
    }

    @Test
    fun `GIVEN an unreachable hub WHEN the members list is shown THEN it says the hub is unreachable`() {
        val hub = FakeMembersHub()
        hub.isOffline()

        runScreenTest {
            membersScreen(hub)

            onMembers { seesTheHubIsUnreachable() }
        }
    }

    @Test
    fun `GIVEN a members list that is still loading WHEN it is shown THEN the title and placeholder blocks show and invite does not`() {
        runComposeUiTest {
            membersContent(MembersUiState(status = MembersStatus.Loading))

            onMembers {
                // The title and the lead need no hub, so they are there from the first frame.
                seesTheTitle()
                seesBlocksWhereTheMembersWillBe()
                // Whether this phone may invite anyone is one of the things the hub has not said yet.
                doesNotSeeInvite()
            }
        }
    }

    @Test
    fun `GIVEN a members list with rows that is loading again WHEN it is shown THEN the rows stay readable with a bar under the header and no blocks`() {
        runComposeUiTest {
            membersContent(
                MembersUiState(
                    rows = MembersUiStateProvider().values.first().rows,
                    youAreAdmin = true,
                    status = MembersStatus.Loading,
                ),
            )

            onMembers {
                // Pattern 3: what is already on screen is never replaced by blocks.
                seesMember("Emma Larsson")
                seesTheBarUnderTheHeader()
                doesNotSeeBlocks()
            }
        }
    }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the title is there`() {
        val states = MembersUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                membersContent(state)

                onMembers { seesTheTitle() }
            }
        }
    }
}
