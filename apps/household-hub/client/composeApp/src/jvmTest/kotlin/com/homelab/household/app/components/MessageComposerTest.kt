package com.homelab.household.app.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.theme.HearthTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MessageComposerTest {

    @Test
    fun send_passes_the_current_text() = runComposeUiTest {
        var text by mutableStateOf("")
        var sent: String? = null
        setContent {
            HearthTheme(darkTheme = false) {
                MessageComposer(
                    value = text,
                    onValueChange = { text = it },
                    onSend = { sent = it },
                    placeholder = "Message the Coordinator…"
                )
            }
        }

        onNode(hasSetTextAction()).performTextInput("What's left this week?")
        onNodeWithContentDescription("Send").performClick()

        assertEquals("What's left this week?", sent)
    }

    @Test
    fun send_is_always_clickable_even_when_empty() = runComposeUiTest {
        var sends = 0
        setContent {
            HearthTheme(darkTheme = false) {
                MessageComposer(value = "", onValueChange = {}, onSend = { sends++ }, placeholder = "Message…")
            }
        }

        onNodeWithContentDescription("Send").assertIsEnabled().assertHasClickAction().performClick()

        assertEquals(1, sends)
    }

    @Test
    fun the_composer_never_clears_the_text_itself() = runComposeUiTest {
        var text by mutableStateOf("Move dinner to 20:00")
        setContent {
            HearthTheme(darkTheme = false) {
                MessageComposer(value = text, onValueChange = { text = it }, onSend = {}, placeholder = "Message…")
            }
        }

        onNodeWithContentDescription("Send").performClick()

        onNode(hasSetTextAction()).assertTextContains("Move dinner to 20:00")
    }

    @Test
    fun shows_the_placeholder_when_empty() = runComposeUiTest {
        setContent {
            HearthTheme(darkTheme = false) {
                MessageComposer(value = "", onValueChange = {}, onSend = {}, placeholder = "Message the Coordinator…")
            }
        }

        onNodeWithText("Message the Coordinator…").assertExists()
    }
}
