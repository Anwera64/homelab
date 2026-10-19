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
    fun `GIVEN the chats list WHEN it is shown THEN each row shows its title and preview and the agent's face`() =
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
    fun `GIVEN the chats list WHEN a row is tapped THEN that conversation opens`() =
        runComposeUiTest {
            val opened = mutableListOf<String>()
            chatsScreen(stateNamed("The list"), onOpen = { opened += it })

            onChats { tapsChat("Saturday dinner") }

            assertEquals(listOf("s-1"), opened)
        }

    @Test
    fun `GIVEN the chats list WHEN text is typed in the search field THEN the text reaches the view model`() =
        runComposeUiTest {
            val typed = mutableListOf<String>()
            chatsScreen(stateNamed("The list"), onSearch = { typed += it })

            onChats { typesInTheSearch("din") }

            assertEquals(listOf("din"), typed)
        }

    @Test
    fun `GIVEN a search in progress WHEN cancel is tapped THEN the search is cancelled`() =
        runComposeUiTest {
            var cancelled = false
            chatsScreen(stateNamed("Searching"), onCancelSearch = { cancelled = true })

            onChats { tapsCancel() }

            assertEquals(true, cancelled)
        }

    @Test
    fun `GIVEN a query that matches nothing WHEN the list is shown THEN it names the query and says what search cannot do`() =
        runComposeUiTest {
            chatsScreen(stateNamed("Nothing matched"))

            onChats {
                seesNoMatchesFor("boiler")
                // The footnote is why a miss is not mistaken for a lost chat.
                seesWhatSearchCannotDo()
            }
        }

    @Test
    fun `GIVEN a household that has never chatted WHEN the list is shown and start a chat is tapped THEN the empty state shows and a new chat starts`() =
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
    fun `GIVEN a household that has never chatted WHEN the list is shown THEN there is no search field`() =
        runComposeUiTest {
            chatsScreen(stateNamed("Never chatted"))

            onChats { doesNotSeeTheSearch() }
        }

    @Test
    fun `GIVEN an unreachable hub WHEN the list is shown and try again is tapped THEN it says the hub is unreachable and retries`() =
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
    fun `GIVEN a conversation whose agent is gone WHEN the list is shown THEN its row is still drawn`() =
        runComposeUiTest {
            chatsScreen(stateNamed("An agent that is gone"))

            onChats {
                seesChat("Is the 5080 worth it?")
                seesChat("That settles it, thanks")
            }
        }

    @Test
    fun `GIVEN every previewed state WHEN it is drawn THEN the title is there`() {
        provider.values.toList().forEach { state ->
            runComposeUiTest {
                chatsScreen(state)

                onChats { seesTheTitle() }
            }
        }
    }
}
