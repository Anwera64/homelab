package com.homelab.household.app.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
            onNodeWithText("Panel review at 14:30").assertIsDisplayed()
            onAllNodes(hasText("**", substring = true)).assertCountEquals(0)
        }

    @Test
    fun `GIVEN your own question with stars in it WHEN it is shown THEN the stars stay`() =
        runComposeUiTest {
            // GIVEN
            val question = "What does **bold** mean here?"

            // WHEN
            setContent { StillTheme { MessageBubble(content = question, fromMe = true) } }

            // THEN
            onNodeWithText(question).assertIsDisplayed()
        }

    @Test
    fun `GIVEN an answer with a list WHEN it is shown THEN each item has its own bullet`() =
        runComposeUiTest {
            // GIVEN
            val answer = "- Milk\n- Bread"

            // WHEN
            setContent { StillTheme { MessageBubble(content = answer, fromMe = false) } }

            // THEN
            onNodeWithText("Milk").assertIsDisplayed()
            onNodeWithText("Bread").assertIsDisplayed()
            onAllNodesWithText("•").assertCountEquals(2)
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
            onNodeWithText("Crowds").assertIsDisplayed()
            onNodeWithText("Beach: Often High").assertIsDisplayed()
            onNodeWithText("Mountains: Generally Lower").assertIsDisplayed()
        }

    @Test
    fun `GIVEN two paragraphs WHEN they are shown THEN a block's gap sits between them`() =
        runComposeUiTest {
            // GIVEN
            val answer = "Tomorrow is light.\n\nNothing in the evening."

            // WHEN
            setContent { StillTheme { MessageBubble(content = answer, fromMe = false) } }

            // THEN
            val first = onNodeWithText("Tomorrow is light.").getUnclippedBoundsInRoot()
            val second = onNodeWithText("Nothing in the evening.").getUnclippedBoundsInRoot()
            assertEquals(DefaultSpacing.md, second.top - first.bottom)
        }

    @Test
    fun `GIVEN a heading after a paragraph WHEN it is shown THEN it sits a heading's gap below`() =
        runComposeUiTest {
            // GIVEN
            val answer = "Here’s the plan:\n\n### Saturday"

            // WHEN
            setContent { StillTheme { MessageBubble(content = answer, fromMe = false) } }

            // THEN
            val intro = onNodeWithText("Here’s the plan:").getUnclippedBoundsInRoot()
            val heading = onNodeWithText("Saturday").getUnclippedBoundsInRoot()
            assertEquals(DefaultSpacing.xl, heading.top - intro.bottom)
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
            onNodeWithText("the forecast").performClick()

            // THEN
            assertEquals(listOf("https://met.no/x"), opened)
        }
}
