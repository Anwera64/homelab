package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.theme.HearthTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** One pattern everywhere: the screen's own icon on a soft tile, a plain title, one line (design notes §6.17). */
@OptIn(ExperimentalTestApi::class)
class EmptyStateTest {

    @Test
    fun shows_icon_title_and_line() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                EmptyState(
                    icon = HearthIcon.Memory,
                    title = "Nothing remembered yet",
                    line = "What agents learn about you will appear here."
                )
            }
        }

        onNodeWithTag(EmptyStateIconTag).assertIsDisplayed()
        onNodeWithText("Nothing remembered yet").assertIsDisplayed()
        onNodeWithText("What agents learn about you will appear here.").assertIsDisplayed()
    }

    @Test
    fun the_action_appears_only_when_given() = runComposeUiTest {
        var started = 0
        setContent {
            HearthTheme(darkTheme = false) {
                EmptyState(
                    icon = HearthIcon.Chats,
                    title = "No chats yet",
                    line = "Pick an agent and ask it anything.",
                    action = EmptyStateAction(label = "Start a chat", onClick = { started++ })
                )
            }
        }

        onNodeWithText("Start a chat").assertIsDisplayed().performClick()
        assertEquals(1, started)
    }

    @Test
    fun without_an_action_there_is_no_button() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                EmptyState(icon = HearthIcon.Document, title = "No notes yet", line = "Notes agents write will appear here.")
            }
        }

        onNodeWithText("Start a chat").assertDoesNotExist()
    }
}
