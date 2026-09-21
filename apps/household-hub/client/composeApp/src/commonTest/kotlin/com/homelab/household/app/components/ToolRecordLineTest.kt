package com.homelab.household.app.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.testing.StillTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tools leave a trail: every read leaves a quiet record above the answer (design notes §2). */
@OptIn(ExperimentalTestApi::class)
class ToolRecordLineTest {

    @Test
    fun shows_what_the_tool_did() = runComposeUiTest {
        setContent {
            StillTheme {
                ToolRecordLine(icon = HearthIcon.Schedule, text = "Checked your calendar")
            }
        }

        onNodeWithText("Checked your calendar").assertIsDisplayed()
    }

    @Test
    fun is_not_clickable_without_on_click() = runComposeUiTest {
        setContent {
            StillTheme {
                ToolRecordLine(icon = HearthIcon.Schedule, text = "Checked your calendar")
            }
        }

        onNode(hasText("Checked your calendar") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun opens_when_on_click_is_provided() = runComposeUiTest {
        var opened = 0
        setContent {
            StillTheme {
                ToolRecordLine(icon = HearthIcon.Search, text = "Searched the web", onClick = { opened++ })
            }
        }

        onNodeWithText("Searched the web").assertHasClickAction().performClick()
        assertEquals(1, opened)
    }
}
