package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** One pattern everywhere: the screen's own icon on a soft tile, a plain title, one line (design notes §6.17). */
@OptIn(ExperimentalTestApi::class)
class EmptyStateTest {
    @Test
    fun `GIVEN an empty state with an icon a title and a line WHEN it is shown THEN all three are visible`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    EmptyState(
                        icon = HearthIcon.Memory,
                        title = "Nothing remembered yet",
                        line = "What agents learn about you will appear here.",
                    )
                }
            }

            onEmptyState {
                seesTheIcon()
                seesText("Nothing remembered yet")
                seesText("What agents learn about you will appear here.")
            }
        }

    @Test
    fun `GIVEN an empty state with an action WHEN the action is tapped THEN the action's handler runs once`() =
        runComposeUiTest {
            var started = 0
            setContent {
                StillTheme {
                    EmptyState(
                        icon = HearthIcon.Chats,
                        title = "No chats yet",
                        line = "Pick an agent and ask it anything.",
                        action = EmptyStateAction(label = "Start a chat", onClick = { started++ }),
                    )
                }
            }

            onEmptyState { tapsTheAction("Start a chat") }
            assertEquals(1, started)
        }

    @Test
    fun `GIVEN an empty state without an action WHEN it is shown THEN no action button appears`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    EmptyState(
                        icon = HearthIcon.Document,
                        title = "No notes yet",
                        line = "Notes agents write will appear here.",
                    )
                }
            }

            onEmptyState { doesNotSeeTheAction("Start a chat") }
        }
}
