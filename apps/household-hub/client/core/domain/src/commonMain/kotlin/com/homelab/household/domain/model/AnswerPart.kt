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

    /** [summary] is what the step shows about itself; null from a hub that did not send one. */
    data class ToolDone(
        val tool: String,
        val summary: ToolSummary? = null,
    ) : AnswerPart

    data class ToolFailed(
        val tool: String,
        val summary: ToolSummary? = null,
    ) : AnswerPart

    /**
     * A write the agent wants to make, waiting on the member: the approval card.
     *
     * [details] are what the model asked for, as the phone reads them. Once every card of the step
     * has an answer the turn carries on, and the hub turns each into a [ToolDone] or a [Declined]
     * where it stood.
     */
    data class Proposal(
        val toolCallId: String,
        val tool: String,
        val action: ToolAction?,
        val details: ProposalDetails = ProposalDetails.Other,
        val status: ProposalStatus = ProposalStatus.Pending,
    ) : AnswerPart

    /** A write the member said no to. [summary] names it, the way a done write is named. */
    data class Declined(
        val tool: String,
        val summary: ToolSummary? = null,
    ) : AnswerPart
}

/** Where a card stands. A decided card waits only for the rest of its step's cards. */
enum class ProposalStatus {
    Pending,
    Approved,
    Declined,
}
