package com.homelab.household.domain.model

/**
 * One piece of an answer, in the order it happened: a stretch of text, a stretch of thinking, or
 * a tool used between them.
 *
 * An answer used to be its text plus a trail of what it did, which could not say where in the
 * text a tool ran: every tool was drawn above the whole answer, and the words written before a
 * tool ran into the ones after it (#33). Tools keep the backend's name; turning it into words for
 * a person is the screen's job.
 */
sealed interface AnswerPart {
    data class Text(
        val content: String,
    ) : AnswerPart

    /** One stretch of thinking, timed in whole seconds, never zero. Never what was thought. */
    data class Thought(
        val seconds: Int,
    ) : AnswerPart

    data class ToolDone(
        val tool: String,
    ) : AnswerPart

    data class ToolFailed(
        val tool: String,
    ) : AnswerPart
}
