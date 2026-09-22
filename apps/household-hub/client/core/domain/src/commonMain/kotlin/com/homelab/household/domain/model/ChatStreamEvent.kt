package com.homelab.household.domain.model

sealed interface ChatStreamEvent {
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
    ) : ChatStreamEvent
}
