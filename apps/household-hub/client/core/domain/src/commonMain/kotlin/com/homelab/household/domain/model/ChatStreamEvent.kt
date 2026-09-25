package com.homelab.household.domain.model

sealed interface ChatStreamEvent {
    /**
     * The hub has written the question down.
     *
     * The earliest honest thing it can say, and the line between the two kinds of failure: before
     * it, a broken turn means the question never arrived; after it, the question is safe and only
     * the answer is in trouble — so nothing after this should ever offer to send it again.
     */
    data object Accepted : ChatStreamEvent

    /**
     * What a thinking model is saying to itself before it answers.
     *
     * Worth watching while it happens and worth nothing afterwards: it is never part of the answer
     * and the hub does not keep it.
     */
    data class Reasoning(
        val content: String,
    ) : ChatStreamEvent

    data class Delta(
        val content: String,
    ) : ChatStreamEvent

    data class ToolExecuting(
        val tool: String,
        val arguments: Map<String, Any?> = emptyMap(),
    ) : ChatStreamEvent

    data class ToolResult(
        val tool: String,
        val success: Boolean,
        val data: Any? = null,
        val error: String? = null,
        /** What the step shows on the phone, the same as the saved part will carry (#40). */
        val summary: ToolSummary? = null,
    ) : ChatStreamEvent

    data class ToolApprovalProposal(
        val tool: String,
        val arguments: Map<String, Any?> = emptyMap(),
        val message: String = "",
    ) : ChatStreamEvent

    data class Done(
        val messageId: String,
        val assistantContent: String,
        val suggestSecretMode: Boolean = false,
        val isTurnSecret: Boolean = false,
        val agentName: String = "",
        val toolsExecuted: List<Map<String, Any?>> = emptyList(),
        /** The answer as the hub saved it, in order. Empty from a hub that does not keep parts yet. */
        val parts: List<AnswerPart> = emptyList(),
    ) : ChatStreamEvent

    /**
     * The stream is gone and the answer is being fetched instead.
     *
     * Emitted once, when recovery starts. Without it the phone would sit on a half-written answer
     * with no sign that anything was still happening — the recovery is silent by nature, since it
     * is polling rather than streaming.
     */
    data object Reconnecting : ChatStreamEvent

    /**
     * The active wait ended; the turn did not.
     *
     * Durable execution means the hub keeps answering after the phone stops listening, so giving
     * up on waiting is not the answer failing. This ends the flow normally — a thrown exception
     * here would reach a person as "something went wrong" when nothing has, and the answer is
     * still being written.
     */
    data object StillWorking : ChatStreamEvent

    /**
     * The turn never got as far as an answer, and the question is not on the hub either.
     *
     * The hub says this when a turn fails before the question is written down — an archived
     * conversation, an agent that has been deactivated, a session that is not yours. Distinct from
     * [TurnFailed], which is the opposite case and the reason both exist: one is worth asking for
     * the answer again, the other is worth marking on the question and sending it once more.
     *
     * Carries the hub's own words, for the line under the composer.
     */
    data class StreamError(
        val message: String,
    ) : ChatStreamEvent

    /**
     * The hub is not working on this conversation and no answer arrived, so the turn died.
     *
     * Distinct from [StillWorking] because only this one is worth offering to do again, and
     * distinct from a thrown failure because the question itself was delivered: what failed is the
     * answer, which is a different sentence on screen and a different button under it.
     */
    data object TurnFailed : ChatStreamEvent
}
