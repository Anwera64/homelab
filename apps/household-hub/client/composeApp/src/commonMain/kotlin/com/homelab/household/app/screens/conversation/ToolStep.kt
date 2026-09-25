package com.homelab.household.app.screens.conversation

import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_reason_blocked
import com.homelab.household.app.resources.tool_reason_forbidden
import com.homelab.household.app.resources.tool_reason_not_a_page
import com.homelab.household.app.resources.tool_reason_not_found
import com.homelab.household.app.resources.tool_reason_search_unavailable
import com.homelab.household.app.resources.tool_reason_site_unavailable
import com.homelab.household.app.resources.tool_reason_throttled
import com.homelab.household.app.resources.tool_reason_too_large
import com.homelab.household.app.resources.tool_reason_unreadable
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSource
import com.homelab.household.domain.model.ToolSummary
import org.jetbrains.compose.resources.StringResource

/**
 * A tool step, as it is put to a person (#40): its tool's own icon, red when it [failed], the
 * [words] on its line, and the [results] a search opens to.
 *
 * Decided here from what the hub saved with the step, so the screen only draws. A step saved
 * before the hub kept summaries has plain words and nothing to open, exactly as it read before.
 */
data class ToolStep(
    val icon: HearthIcon,
    val failed: Boolean,
    val words: StepWords,
    val results: List<ToolSource> = emptyList(),
)

/** The line a step shows. Queries, titles and sites are shown as saved; everything else is a string resource. */
sealed interface StepWords {
    /** The tool's own words and nothing more: "Checked your calendar". */
    data class Plain(
        val words: StringResource,
    ) : StepWords

    /** "Searched the web for “dinner Gràcia” · 5 results". */
    data class Searched(
        val query: String,
        val count: Int,
    ) : StepWords

    /** "Read Menu and opening hours · lapubilla.cat", the title a link to [url]. */
    data class Read(
        val title: String,
        val host: String,
        val url: String,
    ) : StepWords

    /** "Couldn’t read scmp.com · it blocks automated reading". */
    data class CouldNotRead(
        val host: String,
        val reason: StringResource?,
    ) : StepWords

    /** "Couldn’t search the web · the hub’s search service isn’t answering"; the reason only when one is known. */
    data class Failed(
        val words: StringResource,
        val reason: StringResource?,
    ) : StepWords

    /** "Added to your calendar · Dinner together". */
    data class Named(
        val words: StringResource,
        val name: String,
    ) : StepWords
}

fun toolStep(part: AnswerPart.ToolDone): ToolStep = toolStep(part.tool, succeeded = true, part.summary)

fun toolStep(part: AnswerPart.ToolFailed): ToolStep = toolStep(part.tool, succeeded = false, part.summary)

private fun toolStep(
    tool: String,
    succeeded: Boolean,
    summary: ToolSummary?,
): ToolStep {
    val label = toolLabel(tool)
    val page = summary?.sources?.firstOrNull()
    if (!succeeded) {
        val reason = summary?.reason?.let { reasonWords(tool, it) }
        val words =
            if (tool == READ_PAGE && page != null) {
                StepWords.CouldNotRead(hostOf(page.url), reason)
            } else {
                StepWords.Failed(label.failed, reason)
            }
        return ToolStep(label.icon, failed = true, words)
    }
    val query = summary?.query
    return when {
        tool == SEARCH && query != null -> {
            val results = summary.sources
            ToolStep(label.icon, failed = false, StepWords.Searched(query, summary.count ?: results.size), results)
        }

        tool == READ_PAGE && page != null -> {
            val host = hostOf(page.url)
            ToolStep(label.icon, failed = false, StepWords.Read(page.title.ifBlank { host }, host, page.url))
        }

        summary?.title != null -> {
            ToolStep(label.icon, failed = false, StepWords.Named(label.done, summary.title.orEmpty()))
        }

        else -> {
            ToolStep(label.icon, failed = false, StepWords.Plain(label.done))
        }
    }
}

/**
 * Why a step failed, in words. The search service is the hub's own, so its outage is said as such;
 * the same outage while reading is the site's. An unknown cause says nothing rather than guess.
 */
private fun reasonWords(
    tool: String,
    reason: ToolFailureReason,
): StringResource? =
    when (reason) {
        ToolFailureReason.ServiceUnavailable -> {
            if (tool == SEARCH) Res.string.tool_reason_search_unavailable else Res.string.tool_reason_site_unavailable
        }

        ToolFailureReason.Throttled -> {
            Res.string.tool_reason_throttled
        }

        ToolFailureReason.Blocked -> {
            Res.string.tool_reason_blocked
        }

        ToolFailureReason.Forbidden -> {
            Res.string.tool_reason_forbidden
        }

        ToolFailureReason.TooLarge -> {
            Res.string.tool_reason_too_large
        }

        ToolFailureReason.NotAPage -> {
            Res.string.tool_reason_not_a_page
        }

        ToolFailureReason.Unreadable -> {
            Res.string.tool_reason_unreadable
        }

        ToolFailureReason.NotFound -> {
            Res.string.tool_reason_not_found
        }

        ToolFailureReason.Unknown -> {
            null
        }
    }

/** The site a link is on, as a person would say it: `https://www.scmp.com/news` is `scmp.com`. */
fun hostOf(url: String): String {
    val afterScheme = url.substringAfter("://", url)
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
    return host.removePrefix("www.").ifBlank { url }
}

private const val SEARCH = "searxng_search"
private const val READ_PAGE = "read_page"
