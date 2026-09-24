package com.homelab.household.app.screens.conversation

import androidx.compose.runtime.Composable
import com.homelab.household.app.components.ComponentPreview
import com.homelab.household.app.theme.PreviewDayNight
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSource
import com.homelab.household.domain.model.ToolSummary

/**
 * The lines a research turn leaves, each saying what it found or why it couldn't (#40): a search,
 * a page read, one that couldn't be, and a look through what was read. Then the search service
 * down. Canvas: Tools & web search · Research and Web search.
 */
@PreviewDayNight
@Composable
private fun ResearchTrailPreview() {
    val rsf = ToolSource("Hong Kong: press freedom index", "https://rsf.org/en/country/hong-kong")
    val scmp = ToolSource("", "https://www.scmp.com/news/hong-kong")
    val searched = ToolSummary(query = "Hong Kong press freedom", count = 5, sources = listOf(rsf))
    val blocked = ToolSummary(reason = ToolFailureReason.Blocked, sources = listOf(scmp))
    val down = ToolSummary(reason = ToolFailureReason.ServiceUnavailable)
    ComponentPreview {
        ToolStepLine(AnswerPart.ToolDone("searxng_search", searched))
        ToolStepLine(AnswerPart.ToolDone("read_page", ToolSummary(sources = listOf(rsf))))
        ToolStepLine(AnswerPart.ToolFailed("read_page", blocked))
        ToolStepLine(AnswerPart.ToolDone("lookup_sources"))
        ToolStepLine(AnswerPart.ToolFailed("searxng_search", down))
    }
}
