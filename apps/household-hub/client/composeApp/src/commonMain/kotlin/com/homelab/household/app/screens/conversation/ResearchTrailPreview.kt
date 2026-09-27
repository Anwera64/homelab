package com.homelab.household.app.screens.conversation

import androidx.compose.runtime.Composable
import com.homelab.household.app.components.ComponentPreview
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSource
import com.homelab.household.domain.model.ToolSummary

/**
 * The lines a research turn leaves, each saying what it found or why it couldn't (#40): a search,
 * a page read, one whose long title gives way to its site, one that couldn't be, and a look
 * through what was read. Then the search service down. Canvas: Tools & web search · Research and
 * Web search.
 */
@PreviewDayNight
@Composable
private fun ResearchTrailPreview() {
    val rsf = ToolSource("Hong Kong: press freedom index", "https://rsf.org/en/country/hong-kong")
    val long =
        ToolSource(
            "Hong Kong: press freedom in decline four years after the national security law came into force",
            "https://hkfp.com/2024/press-freedom",
        )
    val scmp = ToolSource("", "https://www.scmp.com/news/hong-kong")
    val searched = ToolSummary(query = "Hong Kong press freedom", count = 5, sources = listOf(rsf))
    val blocked = ToolSummary(reason = ToolFailureReason.Blocked, sources = listOf(scmp))
    val down = ToolSummary(reason = ToolFailureReason.ServiceUnavailable)
    ComponentPreview {
        ToolStepLine(AnswerPart.ToolDone("searxng_search", searched))
        ToolStepLine(AnswerPart.ToolDone("read_page", ToolSummary(sources = listOf(rsf))))
        ToolStepLine(AnswerPart.ToolDone("read_page", ToolSummary(sources = listOf(long))))
        ToolStepLine(AnswerPart.ToolFailed("read_page", blocked))
        ToolStepLine(AnswerPart.ToolDone("lookup_sources"))
        ToolStepLine(AnswerPart.ToolFailed("searxng_search", down))
    }
}

/**
 * What a write did, by its action: an add, a change and a removal of an event, a note replaced,
 * and a removal that failed. A removal never reads as an add. Canvas: Tools · tools-map.
 */
@PreviewDayNight
@Composable
private fun WriteStepsPreview() {
    ComponentPreview {
        ToolStepLine(
            AnswerPart.ToolDone("calendar_write", ToolSummary(action = ToolAction.Create, title = "Dinner together")),
        )
        ToolStepLine(AnswerPart.ToolDone("calendar_write", ToolSummary(action = ToolAction.Update, title = "Dentist")))
        ToolStepLine(
            AnswerPart.ToolDone("calendar_write", ToolSummary(action = ToolAction.Delete, title = "Print shop cutoff")),
        )
        ToolStepLine(AnswerPart.ToolDone("document_writer", ToolSummary(action = ToolAction.Replace)))
        ToolStepLine(AnswerPart.ToolFailed("calendar_write", ToolSummary(action = ToolAction.Delete)))
    }
}
