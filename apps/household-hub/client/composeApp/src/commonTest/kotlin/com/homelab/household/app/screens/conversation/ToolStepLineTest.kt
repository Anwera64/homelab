package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.homelab.household.app.testing.StillTheme
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSource
import com.homelab.household.domain.model.ToolSummary
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

            onToolStepLine {
                seesTheSearch("dinner Gràcia", count = 2)
                doesNotSeeResult(guide.title)
            }
        }

    @Test
    fun `GIVEN a search WHEN tapped THEN its results open in place and tapping again closes them`() =
        runComposeUiTest {
            setContent { StillTheme { ToolStepLine(search) } }

            onToolStepLine {
                tapsTheSearch("dinner Gràcia", count = 2)

                seesResult(guide.title, site = "timeout.com")
                seesResult(menu.title, site = "lapubilla.cat")

                tapsTheSearch("dinner Gràcia", count = 2)

                doesNotSeeResult(guide.title)
            }
        }

    @Test
    fun `GIVEN an opened search WHEN drawn THEN each result's title is a link to it`() =
        runComposeUiTest {
            setContent { StillTheme { ToolStepLine(search) } }

            onToolStepLine {
                tapsTheSearch("dinner Gràcia", count = 2)
                seesResult(guide.title, site = "timeout.com")

                assertEquals(listOf(guide.url), linksInResult(guide.title))
            }
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

            onToolStepLine {
                seesThePageRead(menu.title)
                seesThePageSite(menu.title, site = "lapubilla.cat")
                assertEquals(listOf(menu.url), linksInPageRead(menu.title))
            }
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

            onToolStepLine {
                val title = layoutOfPageTitle(long.title)
                assertEquals(1, title.lineCount)
                assertFalse(title.isShownWhole(), "the title is shortened")

                val site = layoutOfPageSite(long.title, site = "rsf.org")
                assertEquals(1, site.lineCount)
                assertTrue(site.isShownWhole(), "the site is shown whole")
            }
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

            onToolStepLine {
                assertTrue(layoutOfPageTitle(menu.title).isShownWhole())
                assertTrue(layoutOfPageSite(menu.title, site = "lapubilla.cat").isShownWhole())
            }
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

            onToolStepLine { seesThePageWasBlocked(site = "scmp.com") }
        }

    @Test
    fun `GIVEN the search service down WHEN drawn THEN it says why`() =
        runComposeUiTest {
            val down = ToolSummary(query = "q", reason = ToolFailureReason.ServiceUnavailable)
            setContent { StillTheme { ToolStepLine(AnswerPart.ToolFailed("searxng_search", down)) } }

            onToolStepLine { seesWhyTheSearchServiceIsDown() }
        }

    @Test
    fun `GIVEN a search saved before summaries WHEN drawn THEN it reads as it always did and does not open`() =
        runComposeUiTest {
            setContent { StillTheme { ToolStepLine(AnswerPart.ToolDone("searxng_search")) } }

            onToolStepLine {
                tapsTheOldSearch()
                seesNoDetailAfterTheWords()
            }
        }

    /**
     * Every character shown, with room for all of it. Asked this way because on the JVM, where these
     * run, the text layout reports no line as ellipsized and counts unwrapped text as overflowing.
     */
    private fun TextLayoutResult.isShownWhole(): Boolean =
        getLineEnd(lineCount - 1, visibleEnd = true) == layoutInput.text.length &&
            size.width >= ceil(multiParagraph.maxIntrinsicWidth)
}
