package com.homelab.household.app.screens.conversation

import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A closed fold says what its steps did, not how many there were (#40; canvas: Explanatory
 * steps). Thinking and looking through the sources are how an answer got there, not what it found,
 * so they are neither named nor counted.
 */
class StepsLabelTest {
    private val search = AnswerPart.ToolDone("searxng_search")
    private val read = AnswerPart.ToolDone("read_page")
    private val blockedRead = AnswerPart.ToolFailed("read_page", ToolSummary(reason = ToolFailureReason.Blocked))
    private val lookup = AnswerPart.ToolDone("lookup_sources")
    private val checked = AnswerPart.ToolDone("calendar_read")
    private val thought = AnswerPart.Thought(3)

    @Test
    fun `GIVEN a single step WHEN labelled THEN it is not folded at all`() {
        assertNull(stepsLabel(listOf(checked)))
        assertNull(stepsLabel(listOf(thought)))
    }

    @Test
    fun `GIVEN a search and two reads WHEN labelled THEN each kind is one phrase in the order it first ran`() {
        val label = stepsLabel(listOf(search, read, thought, read))

        assertEquals(StepsLabel(listOf(StepPhrase.Searched(1), StepPhrase.Read(2))), label)
    }

    @Test
    fun `GIVEN reads before a search WHEN labelled THEN the reads come first`() {
        assertEquals(listOf(StepPhrase.Read(1), StepPhrase.Searched(1)), stepsLabel(listOf(read, search))?.phrases)
    }

    @Test
    fun `GIVEN repeated searches WHEN labelled THEN they are counted`() {
        assertEquals(listOf(StepPhrase.Searched(4)), stepsLabel(List(4) { search })?.phrases)
    }

    @Test
    fun `GIVEN a read that failed among others WHEN labelled THEN it is counted as failed and not as read`() {
        val label = stepsLabel(listOf(search, read, blockedRead, read, lookup))

        assertEquals(StepsLabel(listOf(StepPhrase.Searched(1), StepPhrase.Read(2)), failed = 1), label)
    }

    @Test
    fun `GIVEN thinking and looking through the sources WHEN labelled THEN they are neither named nor counted`() {
        assertEquals(StepsLabel(listOf(StepPhrase.Searched(1))), stepsLabel(listOf(thought, search, lookup, thought)))
    }

    @Test
    fun `GIVEN an added event WHEN labelled THEN the outcome is named`() {
        val added = AnswerPart.ToolDone("calendar_write", ToolSummary(title = "Dinner together"))

        assertEquals(
            listOf(StepPhrase.CheckedCalendar, StepPhrase.Added("Dinner together")),
            stepsLabel(listOf(checked, added))?.phrases,
        )
    }

    @Test
    fun `GIVEN more than two kinds WHEN labelled THEN the first two are named and the rest counted`() {
        val label = stepsLabel(listOf(checked, search, read, read))

        assertEquals(StepsLabel(listOf(StepPhrase.CheckedCalendar, StepPhrase.Searched(1)), more = 2), label)
    }

    @Test
    fun `GIVEN only failures WHEN labelled THEN the label is the failure itself`() {
        val down = AnswerPart.ToolFailed("searxng_search", ToolSummary(reason = ToolFailureReason.ServiceUnavailable))

        assertEquals(StepsLabel(listOf(StepPhrase.Failed("searxng_search"))), stepsLabel(listOf(down, thought)))
    }

    @Test
    fun `GIVEN nothing worth naming WHEN labelled THEN it falls back to counting the steps`() {
        assertEquals(StepsLabel(emptyList()), stepsLabel(listOf(thought, lookup)))
    }

    @Test
    fun `GIVEN a tool without words of its own in a label WHEN labelled THEN it is named by its tool`() {
        assertEquals(
            listOf(StepPhrase.Did("document_writer"), StepPhrase.Searched(1)),
            stepsLabel(listOf(AnswerPart.ToolDone("document_writer"), search))?.phrases,
        )
    }
}
