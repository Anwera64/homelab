package com.homelab.household.app.screens.conversation

import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolAction
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
        val added =
            AnswerPart.ToolDone(
                "calendar_write",
                ToolSummary(action = ToolAction.Create, title = "Dinner together"),
            )

        assertEquals(
            listOf(StepPhrase.CheckedCalendar, StepPhrase.Wrote(ToolAction.Create, "Dinner together")),
            stepsLabel(listOf(checked, added))?.phrases,
        )
    }

    @Test
    fun `GIVEN a removed event WHEN labelled THEN it is named as removed and never added`() {
        val removed =
            AnswerPart.ToolDone(
                "calendar_write",
                ToolSummary(action = ToolAction.Delete, title = "Print shop"),
            )

        assertEquals(
            listOf(StepPhrase.CheckedCalendar, StepPhrase.Wrote(ToolAction.Delete, "Print shop")),
            stepsLabel(listOf(checked, removed))?.phrases,
        )
    }

    @Test
    fun `GIVEN an add and a removal WHEN labelled THEN each is its own phrase`() {
        val added = AnswerPart.ToolDone("calendar_write", ToolSummary(action = ToolAction.Create, title = "Dinner"))
        val removed = AnswerPart.ToolDone("calendar_write", ToolSummary(action = ToolAction.Delete, title = "Lunch"))

        assertEquals(
            listOf(StepPhrase.Wrote(ToolAction.Delete, "Lunch"), StepPhrase.Wrote(ToolAction.Create, "Dinner")),
            stepsLabel(listOf(removed, added))?.phrases,
        )
    }

    @Test
    fun `GIVEN a failed removal alone WHEN labelled THEN the label is that removal failing`() {
        val failed = AnswerPart.ToolFailed("calendar_write", ToolSummary(action = ToolAction.Delete))

        assertEquals(
            StepsLabel(listOf(StepPhrase.Failed("calendar_write", ToolAction.Delete))),
            stepsLabel(listOf(thought, failed)),
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

    @Test
    fun `GIVEN a note write WHEN labelled THEN it is named by what it did to the note`() {
        val appended = AnswerPart.ToolDone("document_writer", ToolSummary(action = ToolAction.Append))

        assertEquals(
            listOf(StepPhrase.Did("document_writer", ToolAction.Append), StepPhrase.Searched(1)),
            stepsLabel(listOf(appended, search))?.phrases,
        )
    }

    @Test
    fun `GIVEN a write done and one declined WHEN labelled THEN the declined one is its own named phrase and not a failure`() {
        val added =
            AnswerPart.ToolDone(
                "calendar_write",
                ToolSummary(title = "Dinner together", action = ToolAction.Create),
            )
        val declined =
            AnswerPart.Declined(
                "calendar_write",
                ToolSummary(title = "Buy flowers", action = ToolAction.Create),
            )

        assertEquals(
            StepsLabel(
                listOf(
                    StepPhrase.Wrote(ToolAction.Create, "Dinner together"),
                    StepPhrase.Declined("calendar_write", ToolAction.Create, "Buy flowers"),
                ),
            ),
            stepsLabel(listOf(added, declined)),
        )
    }
}
