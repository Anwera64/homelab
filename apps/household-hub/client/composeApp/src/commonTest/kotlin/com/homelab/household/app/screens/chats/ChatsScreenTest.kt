package com.homelab.household.app.screens.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.presentation.chats.ChatsUiState
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
            chatsScreen(stateNamed("The list"))

            onChats {
                seesChat("Saturday dinner")
                seesChat("Added. I've let the household know you're both out Saturday evening.")
                seesChat("Jury panel references")
                // Two of the three are the Coordinator's, and the researcher's is its own.
                seesAgentFaces("🏡", count = 2)
                seesAgentFaces("📚", count = 1)
            }
        }

    @Test
    fun tapping_a_row_opens_that_conversation() =
        runComposeUiTest {
            val opened = mutableListOf<String>()
            chatsScreen(stateNamed("The list"), onOpen = { opened += it })

            onChats { tapsChat("Saturday dinner") }

            assertEquals(listOf("s-1"), opened)
        }

    @Test
    fun typing_in_the_search_field_reaches_the_view_model() =
        runComposeUiTest {
            val typed = mutableListOf<String>()
            chatsScreen(stateNamed("The list"), onSearch = { typed += it })

            onChats { typesInTheSearch("din") }

            assertEquals(listOf("din"), typed)
        }

    @Test
    fun cancel_appears_only_while_searching_and_clears_the_query() =
        runComposeUiTest {
            var cancelled = false
            chatsScreen(stateNamed("Searching"), onCancelSearch = { cancelled = true })

            onChats { tapsCancel() }

            assertEquals(true, cancelled)
        }

    @Test
    fun a_query_that_matches_nothing_names_the_query_and_says_what_search_cannot_do() =
        runComposeUiTest {
            chatsScreen(stateNamed("Nothing matched"))

            onChats {
                seesNoMatchesFor("boiler")
                // The footnote is why a miss is not mistaken for a lost chat.
                seesWhatSearchCannotDo()
            }
        }

    @Test
    fun a_household_that_has_never_chatted_gets_the_one_empty_state_with_a_button() =
        runComposeUiTest {
            var started = false
            chatsScreen(stateNamed("Never chatted"), onNewChat = { started = true })

            onChats {
                seesNoChatsYet()
                tapsStartAChat()
            }

            assertEquals(true, started)
        }

    @Test
    fun the_empty_screen_has_no_search_because_there_is_nothing_to_search() =
        runComposeUiTest {
            chatsScreen(stateNamed("Never chatted"))

            onChats { doesNotSeeTheSearch() }
        }

    @Test
    fun an_unreachable_hub_offers_another_go_rather_than_claiming_there_are_no_chats() =
        runComposeUiTest {
            var retried = false
            chatsScreen(stateNamed("Hub unreachable"), onRetry = { retried = true })

            onChats {
                seesTheHubIsUnreachable()
                doesNotSeeNoChatsYet()
                tapsTryAgain()
            }

            assertEquals(true, retried)
        }

    @Test
    fun a_conversation_whose_agent_is_gone_still_draws_its_row() =
        runComposeUiTest {
            chatsScreen(stateNamed("An agent that is gone"))

            onChats {
                seesChat("Is the 5080 worth it?")
                seesChat("That settles it, thanks")
            }
        }

    @Test
    fun every_previewed_state_draws() {
        provider.values.toList().forEach { state ->
            runComposeUiTest {
                chatsScreen(state)

                onChats { seesTheTitle() }
            }
        }
    }
}
