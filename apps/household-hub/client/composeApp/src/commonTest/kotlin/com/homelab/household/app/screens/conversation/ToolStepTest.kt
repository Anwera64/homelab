package com.homelab.household.app.screens.conversation

import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_calendar_read_done
import com.homelab.household.app.resources.tool_calendar_write_done
import com.homelab.household.app.resources.tool_calendar_write_failed
import com.homelab.household.app.resources.tool_reason_blocked
import com.homelab.household.app.resources.tool_reason_search_unavailable
import com.homelab.household.app.resources.tool_reason_site_unavailable
import com.homelab.household.app.resources.tool_reason_throttled
import com.homelab.household.app.resources.tool_search_done
import com.homelab.household.app.resources.tool_search_failed
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSource
import com.homelab.household.domain.model.ToolSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What each tool step says about itself (#40): its own icon, red when it failed, and words built
 * from what the hub saved with it. The words are decided here; [ToolStepLine] only draws them.
 */
class ToolStepTest {
    private val menu = ToolSource("Menu and opening hours", "https://lapubilla.cat/")
    private val guide = ToolSource("The best restaurants in Gràcia", "https://www.timeout.com/gracia")

    @Test
    fun `GIVEN a search with its summary WHEN described THEN it says what it searched for and how much came back and holds the results`() {
        val step =
            toolStep(
                AnswerPart.ToolDone(
                    "searxng_search",
                    ToolSummary(query = "dinner", count = 2, sources = listOf(guide, menu)),
                ),
            )

        assertEquals(StepWords.Searched(query = "dinner", count = 2), step.words)
        assertEquals(listOf(guide, menu), step.results)
        assertEquals(HearthIcon.Search, step.icon)
        assertFalse(step.failed)
    }

    @Test
    fun `GIVEN a search saved before summaries WHEN described THEN it reads as it always did`() {
        val step = toolStep(AnswerPart.ToolDone("searxng_search"))

        assertEquals(StepWords.Plain(Res.string.tool_search_done), step.words)
        assertEquals(emptyList(), step.results)
    }

    @Test
    fun `GIVEN a page read WHEN described THEN it names the page and its site`() {
        val step = toolStep(AnswerPart.ToolDone("read_page", ToolSummary(sources = listOf(menu))))

        assertEquals(
            StepWords.Read(title = "Menu and opening hours", host = "lapubilla.cat", url = "https://lapubilla.cat/"),
            step.words,
        )
        assertEquals(HearthIcon.Document, step.icon)
    }

    @Test
    fun `GIVEN a page read with no title WHEN described THEN its site stands in for it`() {
        val step =
            toolStep(
                AnswerPart.ToolDone(
                    "read_page",
                    ToolSummary(sources = listOf(ToolSource("", "https://www.scmp.com/news"))),
                ),
            )

        assertEquals(
            StepWords.Read(title = "scmp.com", host = "scmp.com", url = "https://www.scmp.com/news"),
            step.words,
        )
    }

    @Test
    fun `GIVEN a page that could not be read WHEN described THEN it names the site and says why with its own icon in red`() {
        val blocked =
            ToolSummary(
                reason = ToolFailureReason.Blocked,
                sources = listOf(ToolSource("", "https://www.scmp.com/news")),
            )

        val step = toolStep(AnswerPart.ToolFailed("read_page", blocked))

        assertEquals(StepWords.CouldNotRead(host = "scmp.com", reason = Res.string.tool_reason_blocked), step.words)
        assertEquals(HearthIcon.Document, step.icon)
        assertTrue(step.failed)
    }

    @Test
    fun `GIVEN the search service down WHEN described THEN it says so in the search's own words`() {
        val step =
            toolStep(
                AnswerPart.ToolFailed(
                    "searxng_search",
                    ToolSummary(query = "q", reason = ToolFailureReason.ServiceUnavailable),
                ),
            )

        assertEquals(
            StepWords.Failed(Res.string.tool_search_failed, Res.string.tool_reason_search_unavailable),
            step.words,
        )
        assertEquals(HearthIcon.Search, step.icon)
    }

    @Test
    fun `GIVEN the search engines throttling WHEN described THEN it says so in the search's own words`() {
        val step =
            toolStep(
                AnswerPart.ToolFailed(
                    "searxng_search",
                    ToolSummary(query = "q", reason = ToolFailureReason.Throttled),
                ),
            )

        assertEquals(
            StepWords.Failed(Res.string.tool_search_failed, Res.string.tool_reason_throttled),
            step.words,
        )
        assertEquals(HearthIcon.Search, step.icon)
    }

    @Test
    fun `GIVEN a site that is not answering WHEN a read fails THEN the reason is about the site and not the hub`() {
        val down = ToolSummary(reason = ToolFailureReason.ServiceUnavailable, sources = listOf(menu))

        val step = toolStep(AnswerPart.ToolFailed("read_page", down))

        assertEquals(
            StepWords.CouldNotRead(host = "lapubilla.cat", reason = Res.string.tool_reason_site_unavailable),
            step.words,
        )
    }

    @Test
    fun `GIVEN a failure nobody could explain WHEN described THEN it says only that it failed with its own icon`() {
        val unknown = toolStep(AnswerPart.ToolFailed("calendar_write", ToolSummary(reason = ToolFailureReason.Unknown)))
        val unsaid = toolStep(AnswerPart.ToolFailed("calendar_write"))

        assertEquals(StepWords.Failed(Res.string.tool_calendar_write_failed, reason = null), unknown.words)
        assertEquals(StepWords.Failed(Res.string.tool_calendar_write_failed, reason = null), unsaid.words)
        assertEquals(HearthIcon.CalendarAdd, unknown.icon)
        assertTrue(unknown.failed)
    }

    @Test
    fun `GIVEN an added event WHEN described THEN it is named`() {
        val step = toolStep(AnswerPart.ToolDone("calendar_write", ToolSummary(title = "Dinner together")))

        assertEquals(StepWords.Named(Res.string.tool_calendar_write_done, "Dinner together"), step.words)
    }

    @Test
    fun `GIVEN a tool with nothing to show WHEN described THEN it keeps its plain words`() {
        assertEquals(
            StepWords.Plain(Res.string.tool_calendar_read_done),
            toolStep(AnswerPart.ToolDone("calendar_read")).words,
        )
    }

    @Test
    fun `GIVEN a link WHEN its site is shown THEN it is the host alone without www`() {
        assertEquals("scmp.com", hostOf("https://www.scmp.com/news/hong-kong?x=1"))
        assertEquals("example.org", hostOf("http://example.org:8080/a"))
        assertEquals("hkja.org.hk", hostOf("https://HKJA.org.hk"))
    }
}
