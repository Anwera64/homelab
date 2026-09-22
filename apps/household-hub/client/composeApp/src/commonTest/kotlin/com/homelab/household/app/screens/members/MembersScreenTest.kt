package com.homelab.household.app.screens.members

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.components.HearthProgressBarTag
import com.homelab.household.app.components.SkeletonGroupTag
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.members_invite
import com.homelab.household.app.resources.members_remove
import com.homelab.household.app.resources.members_reset_pin
import com.homelab.household.app.resources.members_title
import com.homelab.household.app.resources.members_unreachable
import com.homelab.household.app.testing.FakeMembersHub
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.testing.TestApp
import com.homelab.household.app.testing.runScreenTest
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.members.MembersStatus
import com.homelab.household.presentation.members.MembersUiState
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/** The members list, with the real stack under it; only the hub is faked. */
@OptIn(ExperimentalTestApi::class)
class MembersScreenTest {
    private val wait = 5_000L

    /** A phone with somebody signed in on it: these screens are all behind a token. */
    private fun signedIn() = InMemorySessionStorage().apply { saveTokens("signed-in-token") }

    @Test
    fun the_admin_sees_everyone_and_can_invite_reset_and_remove() {
        val hub = FakeMembersHub()
        val reset = mutableListOf<Member>()
        val removed = mutableListOf<Member>()

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn()) {
                    MembersScreen(
                        onBack = {},
                        onInvite = {},
                        onResetPin = { reset += it },
                        onRemove = { removed += it },
                    )
                }
            }

            waitUntilExactlyOneExists(hasText("Liam"), timeoutMillis = wait)
            onNodeWithText("Emma Larsson").assertIsDisplayed()
            onNodeWithText(getString(Res.string.members_invite)).assertIsDisplayed()

            onNodeWithText(getString(Res.string.members_reset_pin)).performClick()
            waitUntil(timeoutMillis = wait) { reset.size == 1 }
            onNodeWithText(getString(Res.string.members_remove)).performClick()
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
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn()) {
                    MembersScreen(onBack = {}, onInvite = {}, onResetPin = {}, onRemove = {})
                }
            }

            waitUntilExactlyOneExists(hasText(getString(Res.string.members_reset_pin)), timeoutMillis = wait)
            onNodeWithText(getString(Res.string.members_invite)).assertDoesNotExist()
            onNodeWithText(getString(Res.string.members_remove)).assertDoesNotExist()
        }
    }

    @Test
    fun an_unreachable_hub_says_so() {
        val hub = FakeMembersHub()
        hub.isOffline()

        runScreenTest {
            setContent {
                TestApp(hub.engine, sessionStorage = signedIn()) {
                    MembersScreen(onBack = {}, onInvite = {}, onResetPin = {}, onRemove = {})
                }
            }

            waitUntilExactlyOneExists(hasText(getString(Res.string.members_unreachable)), timeoutMillis = wait)
        }
    }

    @Test
    fun arriving_empty_draws_the_chrome_and_stands_blocks_where_the_members_will_be() {
        runComposeUiTest {
            setContent {
                StillTheme {
                    MembersContent(
                        state = MembersUiState(status = MembersStatus.Loading),
                        onInvite = {},
                        onResetPin = {},
                        onRemove = {},
                        onRetry = {},
                        onBack = {},
                    )
                }
            }

            // The title and the lead need no hub, so they are there from the first frame.
            onNodeWithText(getString(Res.string.members_title)).assertIsDisplayed()
            onNodeWithTag(SkeletonGroupTag).assertIsDisplayed()
            // Whether this phone may invite anyone is one of the things the hub has not said yet.
            onNodeWithText(getString(Res.string.members_invite)).assertDoesNotExist()
        }
    }

    @Test
    fun coming_back_keeps_the_rows_readable_and_puts_a_bar_under_the_header() {
        runComposeUiTest {
            setContent {
                StillTheme {
                    MembersContent(
                        state =
                            MembersUiState(
                                rows = MembersUiStateProvider().values.first().rows,
                                youAreAdmin = true,
                                status = MembersStatus.Loading,
                            ),
                        onInvite = {},
                        onResetPin = {},
                        onRemove = {},
                        onRetry = {},
                        onBack = {},
                    )
                }
            }

            // Pattern 3: what is already on screen is never replaced by blocks.
            onNodeWithText("Emma Larsson").assertIsDisplayed()
            onNodeWithTag(HearthProgressBarTag).assertIsDisplayed()
            onNodeWithTag(SkeletonGroupTag).assertDoesNotExist()
        }
    }

    @Test
    fun every_previewed_state_draws() {
        val states = MembersUiStateProvider().values.toList()

        states.forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        MembersContent(
                            state = state,
                            onInvite = {},
                            onResetPin = {},
                            onRemove = {},
                            onRetry = {},
                            onBack = {},
                        )
                    }
                }

                onNodeWithText(getString(Res.string.members_title)).assertIsDisplayed()
            }
        }
    }
}
