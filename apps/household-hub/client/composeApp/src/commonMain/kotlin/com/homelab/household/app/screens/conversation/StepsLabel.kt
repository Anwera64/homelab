package com.homelab.household.app.screens.conversation

import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolAction

/**
 * What a closed fold says (#40; canvas: Explanatory steps): what its steps did, not how many there
 * were. "Searched the web, read 2 pages · 1 failed".
 *
 * At most [NAMED_KINDS] kinds are named, in the order each first ran; the steps of any other kind
 * are [more]. [failed] counts the steps that didn't work. No phrases at all means nothing worth
 * naming happened, and the fold counts its steps as it used to.
 */
data class StepsLabel(
    val phrases: List<StepPhrase>,
    val more: Int = 0,
    val failed: Int = 0,
)

/** One kind of step, in words. Never a tool's backend name: [Did] and [Failed] name it through [toolLabel]. */
sealed interface StepPhrase {
    data class Searched(
        val times: Int,
    ) : StepPhrase

    data class Read(
        val pages: Int,
    ) : StepPhrase

    data object CheckedCalendar : StepPhrase

    /**
     * An event written names its outcome, because that is what the answer did: "added Dinner
     * together", "removed Print shop". [action] is null for one saved before the hub said which.
     */
    data class Wrote(
        val action: ToolAction?,
        val title: String?,
    ) : StepPhrase

    /** A tool with no words of its own in a label, named by its usual done words for its [action]. */
    data class Did(
        val tool: String,
        val action: ToolAction? = null,
    ) : StepPhrase

    /** Nothing worked: the label is the failure itself, "Couldn’t search the web". */
    data class Failed(
        val tool: String,
        val action: ToolAction? = null,
    ) : StepPhrase
}

/**
 * The label for a run of [steps], or null when the run is a single step: one step is not folded,
 * it is shown as it is.
 *
 * Thinking and looking through the sources are how an answer got somewhere, not what it did, so
 * they are neither named nor counted.
 */
fun stepsLabel(steps: List<AnswerPart>): StepsLabel? {
    if (steps.size < 2) return null

    val done = steps.filterIsInstance<AnswerPart.ToolDone>().filter { it.tool !in SILENT }
    val failed = steps.filterIsInstance<AnswerPart.ToolFailed>().filter { it.tool !in SILENT }

    // Each kind once, in the order it first ran, with how many steps it stands for. A write's kind
    // is its action too, so adding one event and removing another are never one phrase.
    val kinds = LinkedHashMap<Pair<String, ToolAction?>, MutableList<AnswerPart.ToolDone>>()
    done.forEach { kinds.getOrPut(it.tool to it.summary?.action) { mutableListOf() } += it }

    if (kinds.isEmpty()) {
        val first = failed.firstOrNull() ?: return StepsLabel(emptyList())
        return StepsLabel(listOf(StepPhrase.Failed(first.tool, first.summary?.action)))
    }

    val named = kinds.entries.take(NAMED_KINDS)
    val more = kinds.entries.drop(NAMED_KINDS).sumOf { it.value.size }
    return StepsLabel(
        named.map { (kind, runs) -> phraseFor(kind.first, kind.second, runs) },
        more = more,
        failed = failed.size,
    )
}

private fun phraseFor(
    tool: String,
    action: ToolAction?,
    runs: List<AnswerPart.ToolDone>,
): StepPhrase =
    when (tool) {
        "searxng_search" -> StepPhrase.Searched(runs.size)
        "read_page" -> StepPhrase.Read(runs.size)
        "calendar_read" -> StepPhrase.CheckedCalendar
        "calendar_write" -> StepPhrase.Wrote(action, runs.singleOrNull()?.summary?.title)
        else -> StepPhrase.Did(tool, action)
    }

private const val NAMED_KINDS = 2

private val SILENT = setOf("lookup_sources")
