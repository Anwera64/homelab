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
    fun the_admin_sees_everyone_and_can_invite_reset_and_remove() {
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
    fun a_member_can_vouch_but_not_invite_or_remove() {
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
    fun an_unreachable_hub_says_so() {
        val hub = FakeMembersHub()
        hub.isOffline()

        runScreenTest {
            membersScreen(hub)

            onMembers { seesTheHubIsUnreachable() }
        }
    }

    @Test
    fun arriving_empty_draws_the_chrome_and_stands_blocks_where_the_members_will_be() {
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
    fun coming_back_keeps_the_rows_readable_and_puts_a_bar_under_the_header() {
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
    fun every_previewed_state_draws() {
        val states = MembersUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                membersContent(state)

                onMembers { seesTheTitle() }
            }
        }
    }
}
