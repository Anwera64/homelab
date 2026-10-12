package com.homelab.household.app.screens.chats

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.chats_empty_action
import com.homelab.household.app.resources.chats_empty_title
import com.homelab.household.app.resources.chats_no_matches_line
import com.homelab.household.app.resources.chats_no_matches_title
import com.homelab.household.app.resources.chats_retry
import com.homelab.household.app.resources.chats_search_cancel
import com.homelab.household.app.resources.chats_search_placeholder
import com.homelab.household.app.resources.chats_title
import com.homelab.household.app.resources.chats_unreachable
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.presentation.chats.ChatsUiState
import org.jetbrains.compose.resources.getString

/** Everything the Chats list says and does, named as the resources it reads. */
@OptIn(ExperimentalTestApi::class)
class ChatsRobot(
    private val test: ComposeUiTest,
) {
    suspend fun seesTheTitle() = seesText(getString(Res.string.chats_title))

    /** A chat row, by the title or preview the hub gave it. */
    fun seesChat(text: String) = seesText(text)

    /** How many agents wear [face] across the rows. */
    fun seesAgentFaces(
        face: String,
        count: Int,
    ) {
        test.waitUntilAtLeastOneExists(hasText(face, substring = true), timeoutMillis = WAIT_MILLIS)
        test.onAllNodesWithText(face, substring = true).assertCountEquals(count)
    }

    fun tapsChat(title: String) {
        test.onNodeWithText(title).performClick()
    }

    suspend fun typesInTheSearch(query: String) {
        test.onNodeWithText(getString(Res.string.chats_search_placeholder)).performTextInput(query)
    }

    suspend fun tapsCancel() {
        test.onNodeWithText(getString(Res.string.chats_search_cancel)).performClick()
    }

    suspend fun seesNoMatchesFor(query: String) = seesText(getString(Res.string.chats_no_matches_title, query))

    /** The footnote is why a miss is not mistaken for a lost chat. */
    suspend fun seesWhatSearchCannotDo() = seesText(getString(Res.string.chats_no_matches_line))

    suspend fun seesNoChatsYet() = seesText(getString(Res.string.chats_empty_title))

    suspend fun doesNotSeeNoChatsYet() {
        test.onNodeWithText(getString(Res.string.chats_empty_title)).assertDoesNotExist()
    }

    suspend fun tapsStartAChat() {
        test.onNodeWithText(getString(Res.string.chats_empty_action)).performClick()
    }

    suspend fun doesNotSeeTheSearch() {
        test.onNodeWithText(getString(Res.string.chats_search_placeholder)).assertDoesNotExist()
    }

    suspend fun seesTheHubIsUnreachable() = seesText(getString(Res.string.chats_unreachable))

    suspend fun tapsTryAgain() {
        test.onNodeWithText(getString(Res.string.chats_retry)).performClick()
    }

    private fun seesText(text: String) {
        test.waitUntilExactlyOneExists(hasText(text), timeoutMillis = WAIT_MILLIS)
        test.onNodeWithText(text).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}

@OptIn(ExperimentalTestApi::class)
suspend fun ComposeUiTest.onChats(block: suspend ChatsRobot.() -> Unit) {
    ChatsRobot(this).block()
}

/** The Chats list drawn for [state], with whatever the person does reported to the callbacks. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.chatsScreen(
    state: ChatsUiState,
    onSearch: (String) -> Unit = {},
    onCancelSearch: () -> Unit = {},
    onOpen: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    setContent {
        StillTheme {
            ChatsContent(
                state = state,
                onSearch = onSearch,
                onCancelSearch = onCancelSearch,
                onOpen = onOpen,
                onNewChat = onNewChat,
                onRetry = onRetry,
                tabs = {},
            )
        }
    }
}
