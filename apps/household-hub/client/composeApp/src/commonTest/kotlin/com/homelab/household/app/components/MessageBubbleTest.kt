package com.homelab.household.app.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.app.theme.DefaultSpacing
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A turn as it lands on screen. Answers are the model's Markdown drawn as prose; questions are
 * what you typed, left exactly as you typed them.
 */
@OptIn(ExperimentalTestApi::class)
class MessageBubbleTest {
    @Test
    fun `GIVEN an answer with bold in it WHEN it is shown THEN it reads without the stars`() =
        runComposeUiTest {
            // GIVEN
            val answer = "**Panel review** at 14:30"

            // WHEN
            setContent { StillTheme { MessageBubble(content = answer, fromMe = false) } }

            // THEN
            onMessageBubble {
                seesText("Panel review at 14:30")
                doesNotSeeAnyTextContaining("**")
            }
        }

    @Test
    fun `GIVEN your own question with stars in it WHEN it is shown THEN the stars stay`() =
        runComposeUiTest {
            // GIVEN
            val question = "What does **bold** mean here?"

            // WHEN
            setContent { StillTheme { MessageBubble(content = question, fromMe = true) } }

            // THEN
            onMessageBubble { seesText(question) }
        }

    @Test
    fun `GIVEN an answer with a list WHEN it is shown THEN each item has its own bullet`() =
        runComposeUiTest {
            // GIVEN
            val answer = "- Milk\n- Bread"

            // WHEN
            setContent { StillTheme { MessageBubble(content = answer, fromMe = false) } }

            // THEN
            onMessageBubble {
                seesText("Milk")
                seesText("Bread")
                seesBullets(2)
            }
        }

    @Test
    fun `GIVEN an answer with a table WHEN it is shown THEN each row reads as a label and its values`() =
        runComposeUiTest {
            // GIVEN
            val answer =
                "| Factor | Beach | Mountains |\n| :--- | :--- | :--- |\n" +
                    "| Crowds | Often High | Generally Lower |"

            // WHEN
            setContent { StillTheme { MessageBubble(content = answer, fromMe = false) } }

            // THEN
            onMessageBubble {
                seesText("Crowds")
                seesText("Beach: Often High")
                seesText("Mountains: Generally Lower")
            }
        }

    @Test
    fun `GIVEN two paragraphs WHEN they are shown THEN a block's gap sits between them`() =
        runComposeUiTest {
            // GIVEN
            val answer = "Tomorrow is light.\n\nNothing in the evening."

            // WHEN
            setContent { StillTheme { MessageBubble(content = answer, fromMe = false) } }

            // THEN
            onMessageBubble { seesGapBetween("Tomorrow is light.", "Nothing in the evening.", DefaultSpacing.md) }
        }

    @Test
    fun `GIVEN a heading after a paragraph WHEN it is shown THEN it sits a heading's gap below`() =
        runComposeUiTest {
            // GIVEN
            val answer = "Here’s the plan:\n\n### Saturday"

            // WHEN
            setContent { StillTheme { MessageBubble(content = answer, fromMe = false) } }

            // THEN
            onMessageBubble { seesGapBetween("Here’s the plan:", "Saturday", DefaultSpacing.xl) }
        }

    @Test
    fun `GIVEN an answer that is a link WHEN you tap it THEN its address opens`() =
        runComposeUiTest {
            // GIVEN
            val answer = "[the forecast](https://met.no/x)"
            val opened = mutableListOf<String>()
            val uriHandler =
                object : UriHandler {
                    override fun openUri(uri: String) {
                        opened += uri
                    }
                }
            setContent {
                StillTheme {
                    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                        MessageBubble(content = answer, fromMe = false)
                    }
                }
            }

            // WHEN
            onMessageBubble { tapsText("the forecast") }

            // THEN
            assertEquals(listOf("https://met.no/x"), opened)
        }
}
