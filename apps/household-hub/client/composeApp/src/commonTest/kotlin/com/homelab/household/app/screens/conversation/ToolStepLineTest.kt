package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_search_done
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSource
import com.homelab.household.domain.model.ToolSummary
import org.jetbrains.compose.resources.getString
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A step's line, drawn from what it says about itself (#40). */
@OptIn(ExperimentalTestApi::class)
class ToolStepLineTest {
    private val menu = ToolSource("Menu and opening hours", "https://lapubilla.cat/")
    private val guide = ToolSource("The best restaurants in Gràcia", "https://www.timeout.com/gracia")
    private val search =
        AnswerPart.ToolDone(
            "searxng_search",
            ToolSummary(query = "dinner Gràcia", count = 2, sources = listOf(guide, menu)),
        )

    @Test
    fun `GIVEN a search WHEN drawn THEN its line says what was searched for and how much came back`() =
        runComposeUiTest {
            setContent { StillTheme { ToolStepLine(search) } }

            onNodeWithText("Searched the web for “dinner Gràcia” · 2 results").assertIsDisplayed()
            onNodeWithText(guide.title).assertDoesNotExist()
        }

    @Test
    fun `GIVEN a search WHEN tapped THEN its results open in place and tapping again closes them`() =
        runComposeUiTest {
            setContent { StillTheme { ToolStepLine(search) } }
            val line = "Searched the web for “dinner Gràcia” · 2 results"

            onNodeWithText(line).performClick()

            onNodeWithText(guide.title).assertIsDisplayed()
            onNodeWithText("timeout.com").assertIsDisplayed()
            onNodeWithText(menu.title).assertIsDisplayed()
            onNodeWithText("lapubilla.cat").assertIsDisplayed()

            onNodeWithText(line).performClick()

            onNodeWithText(guide.title).assertDoesNotExist()
        }

    @Test
    fun `GIVEN an opened search WHEN drawn THEN each result's title is a link to it`() =
        runComposeUiTest {
            setContent { StillTheme { ToolStepLine(search) } }
            onNodeWithText("Searched the web for “dinner Gràcia” · 2 results").performClick()

            assertEquals(listOf(guide.url), linksIn(guide.title))
        }

    @Test
    fun `GIVEN a page read WHEN drawn THEN it names the page and its site with the title a link`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    ToolStepLine(
                        AnswerPart.ToolDone("read_page", ToolSummary(sources = listOf(menu))),
                    )
                }
            }

            val read = "Read Menu and opening hours"
            onNodeWithText(read).assertIsDisplayed()
            onNodeWithText(" · lapubilla.cat", useUnmergedTree = true).assertIsDisplayed()
            assertEquals(listOf(menu.url), linksIn(read))
        }

    @Test
    fun `GIVEN a page with a long title WHEN drawn THEN the line stays on one line and shortens the title and never the site`() =
        runComposeUiTest {
            val long =
                ToolSource(
                    "Hong Kong: press freedom in decline four years after the national security law came into force",
                    "https://rsf.org/en/country/hong-kong",
                )
            setContent {
                StillTheme {
                    ToolStepLine(
                        AnswerPart.ToolDone("read_page", ToolSummary(sources = listOf(long))),
                        modifier = Modifier.width(320.dp),
                    )
                }
            }

            val title = layoutOf("Read ${long.title}")
            assertEquals(1, title.lineCount)
            assertFalse(title.isShownWhole(), "the title is shortened")

            val site = layoutOf(" · rsf.org")
            assertEquals(1, site.lineCount)
            assertTrue(site.isShownWhole(), "the site is shown whole")
        }

    @Test
    fun `GIVEN a page with a short title WHEN drawn THEN nothing on its line is shortened`() =
        runComposeUiTest {
            setContent {
                StillTheme {
                    ToolStepLine(
                        AnswerPart.ToolDone("read_page", ToolSummary(sources = listOf(menu))),
                        modifier = Modifier.width(320.dp),
                    )
                }
            }

            assertTrue(layoutOf("Read Menu and opening hours").isShownWhole())
            assertTrue(layoutOf(" · lapubilla.cat").isShownWhole())
        }

    @Test
    fun `GIVEN a page that could not be read WHEN drawn THEN it names the site and says why`() =
        runComposeUiTest {
            val blocked =
                ToolSummary(
                    reason = ToolFailureReason.Blocked,
                    sources = listOf(ToolSource("", "https://www.scmp.com/news")),
                )
            setContent { StillTheme { ToolStepLine(AnswerPart.ToolFailed("read_page", blocked)) } }

            onNodeWithText("Couldn’t read scmp.com · it blocks automated reading").assertIsDisplayed()
        }

    @Test
    fun `GIVEN the search service down WHEN drawn THEN it says why`() =
        runComposeUiTest {
            val down = ToolSummary(query = "q", reason = ToolFailureReason.ServiceUnavailable)
            setContent { StillTheme { ToolStepLine(AnswerPart.ToolFailed("searxng_search", down)) } }

            onNodeWithText("Couldn’t search the web · the hub’s search service isn’t answering").assertIsDisplayed()
        }

    @Test
    fun `GIVEN a search saved before summaries WHEN drawn THEN it reads as it always did and does not open`() =
        runComposeUiTest {
            setContent { StillTheme { ToolStepLine(AnswerPart.ToolDone("searxng_search")) } }

            onNodeWithText(getString(Res.string.tool_search_done)).assertIsDisplayed().performClick()
            onNodeWithText("·", substring = true).assertDoesNotExist()
        }

    private fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        return layouts.single()
    }

    /**
     * Every character shown, with room for all of it. Asked this way because on the JVM, where these
     * run, the text layout reports no line as ellipsized and counts unwrapped text as overflowing.
     */
    private fun TextLayoutResult.isShownWhole(): Boolean =
        getLineEnd(lineCount - 1, visibleEnd = true) == layoutInput.text.length &&
            size.width >= ceil(multiParagraph.maxIntrinsicWidth)

    private fun ComposeUiTest.linksIn(text: String): List<String> {
        val shown = onNodeWithText(text).fetchSemanticsNode().config[SemanticsProperties.Text].first()
        return shown.getLinkAnnotations(0, shown.length).map { (it.item as LinkAnnotation.Url).url }
    }
}
