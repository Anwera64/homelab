package com.homelab.household.app.screens.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What is shown of a model thinking: its newest words, about three lines of them.
 *
 * A text box keeps its first lines and drops the rest, which is exactly backwards here — the
 * newest thought is at the end. So the cut is made before the text is laid out.
 */
class ReasoningTailTest {
    @Test
    fun `GIVEN a short thought WHEN it is shown THEN all of it is`() {
        assertEquals("I need the calendar.", reasoningTail("I need the calendar."))
    }

    @Test
    fun `GIVEN a long thought WHEN it is shown THEN only its end is shown starting on a whole word`() {
        val thought = (1..60).joinToString(" ") { "word$it" }

        val tail = reasoningTail(thought, maxChars = 40)

        assertTrue(tail.startsWith("…"), "a cut thought says it was cut: $tail")
        val kept = tail.removePrefix("…")
        assertTrue(kept.length <= 40, "too long: $kept")
        assertTrue(thought.endsWith(kept), "not the newest words: $kept")
        assertTrue(thought.contains(" $kept"), "starts mid-word: $kept")
    }

    @Test
    fun `GIVEN a thought over several paragraphs WHEN it is shown THEN it reads as one run of text`() {
        assertEquals("First. Second.", reasoningTail("First.\n\nSecond.\n"))
    }
}
