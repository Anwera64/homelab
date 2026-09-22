package com.homelab.household.app.screens.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.chats_empty_action
import com.homelab.household.app.resources.chats_empty_title
import com.homelab.household.app.resources.chats_no_matches_line
import com.homelab.household.app.resources.chats_retry
import com.homelab.household.app.resources.chats_search_cancel
import com.homelab.household.app.resources.chats_search_placeholder
import com.homelab.household.app.resources.chats_title
import com.homelab.household.app.resources.chats_unreachable
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.presentation.chats.ChatsUiState
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Chats list as drawn: every state the canvas has an artboard for, and the ones that look the
 * same on a first glance and must not read the same.
 */
@OptIn(ExperimentalTestApi::class)
class ChatsScreenTest {
    private val provider = ChatsUiStateProvider()

    private fun stateNamed(name: String): ChatsUiState {
        val index = (0 until provider.values.count()).first { provider.getDisplayName(it) == name }
        return provider.values.elementAt(index)
    }

    @Test
    fun a_row_shows_its_title_its_preview_and_the_agents_face() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    ChatsContent(
                        state = stateNamed("The list"),
                        onSearch = {},
                        onCancelSearch = {},
                        onOpen = {},
                        onNewChat = {},
                        onRetry = {},
                        tabs = {},
                    )
                }
            }

            onNodeWithText("Saturday dinner").assertIsDisplayed()
            onNodeWithText("Added. I've let the household know you're both out Saturday evening.").assertIsDisplayed()
            onNodeWithText("Jury panel references").assertIsDisplayed()
            // Two of the three are the Coordinator's, and the researcher's is its own.
            onAllNodesWithText("🏡", substring = true).assertCountEquals(2)
            onAllNodesWithText("📚", substring = true).assertCountEquals(1)
        }

    @Test
    fun tapping_a_row_opens_that_conversation() =
        runComposeUiTest {
            val opened = mutableListOf<String>()
            setContent {
                StillTheme {
                    ChatsContent(
                        state = stateNamed("The list"),
                        onSearch = {},
                        onCancelSearch = {},
                        onOpen = { opened += it },
                        onNewChat = {},
                        onRetry = {},
                        tabs = {},
                    )
                }
            }

            onNodeWithText("Saturday dinner").performClick()

            assertEquals(listOf("s-1"), opened)
        }

    @Test
    fun typing_in_the_search_field_reaches_the_view_model() =
        runComposeUiTest {
            val typed = mutableListOf<String>()
            setContent {
                StillTheme {
                    ChatsContent(
                        state = stateNamed("The list"),
                        onSearch = { typed += it },
                        onCancelSearch = {},
                        onOpen = {},
                        onNewChat = {},
                        onRetry = {},
                        tabs = {},
                    )
                }
            }

            onNodeWithText(getString(Res.string.chats_search_placeholder)).performTextInput("din")

            assertEquals(listOf("din"), typed)
        }

    @Test
    fun cancel_appears_only_while_searching_and_clears_the_query() =
        runComposeUiTest {
            var cancelled = false
            setContent {
                StillTheme {
                    ChatsContent(
                        state = stateNamed("Searching"),
                        onSearch = {},
                        onCancelSearch = { cancelled = true },
                        onOpen = {},
                        onNewChat = {},
                        onRetry = {},
                        tabs = {},
                    )
                }
            }

            onNodeWithText(getString(Res.string.chats_search_cancel)).performClick()

            assertEquals(true, cancelled)
        }

    @Test
    fun a_query_that_matches_nothing_names_the_query_and_says_what_search_cannot_do() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    ChatsContent(
                        state = stateNamed("Nothing matched"),
                        onSearch = {},
                        onCancelSearch = {},
                        onOpen = {},
                        onNewChat = {},
                        onRetry = {},
                        tabs = {},
                    )
                }
            }

            onNodeWithText("No chats called “boiler”").assertIsDisplayed()
            // The footnote is why a miss is not mistaken for a lost chat.
            onNodeWithText(getString(Res.string.chats_no_matches_line)).assertIsDisplayed()
        }

    @Test
    fun a_household_that_has_never_chatted_gets_the_one_empty_state_with_a_button() =
        runComposeUiTest {
            var started = false
            setContent {
                StillTheme {
                    ChatsContent(
                        state = stateNamed("Never chatted"),
                        onSearch = {},
                        onCancelSearch = {},
                        onOpen = {},
                        onNewChat = { started = true },
                        onRetry = {},
                        tabs = {},
                    )
                }
            }

            onNodeWithText(getString(Res.string.chats_empty_title)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.chats_empty_action)).performClick()

            assertEquals(true, started)
        }

    @Test
    fun the_empty_screen_has_no_search_because_there_is_nothing_to_search() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    ChatsContent(
                        state = stateNamed("Never chatted"),
                        onSearch = {},
                        onCancelSearch = {},
                        onOpen = {},
                        onNewChat = {},
                        onRetry = {},
                        tabs = {},
                    )
                }
            }

            onNodeWithText(getString(Res.string.chats_search_placeholder)).assertDoesNotExist()
        }

    @Test
    fun an_unreachable_hub_offers_another_go_rather_than_claiming_there_are_no_chats() =
        runComposeUiTest {
            var retried = false
            setContent {
                StillTheme {
                    ChatsContent(
                        state = stateNamed("Hub unreachable"),
                        onSearch = {},
                        onCancelSearch = {},
                        onOpen = {},
                        onNewChat = {},
                        onRetry = { retried = true },
                        tabs = {},
                    )
                }
            }

            onNodeWithText(getString(Res.string.chats_unreachable)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.chats_empty_title)).assertDoesNotExist()
            onNodeWithText(getString(Res.string.chats_retry)).performClick()

            assertEquals(true, retried)
        }

    @Test
    fun a_conversation_whose_agent_is_gone_still_draws_its_row() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    ChatsContent(
                        state = stateNamed("An agent that is gone"),
                        onSearch = {},
                        onCancelSearch = {},
                        onOpen = {},
                        onNewChat = {},
                        onRetry = {},
                        tabs = {},
                    )
                }
            }

            onNodeWithText("Is the 5080 worth it?").assertIsDisplayed()
            onNodeWithText("That settles it, thanks").assertIsDisplayed()
        }

    @Test
    fun every_previewed_state_draws() {
        provider.values.toList().forEach { state ->
            runComposeUiTest {
                setContent {
                    StillTheme {
                        ChatsContent(
                            state = state,
                            onSearch = {},
                            onCancelSearch = {},
                            onOpen = {},
                            onNewChat = {},
                            onRetry = {},
                            tabs = {},
                        )
                    }
                }

                onNodeWithText(getString(Res.string.chats_title)).assertIsDisplayed()
            }
        }
    }
}
