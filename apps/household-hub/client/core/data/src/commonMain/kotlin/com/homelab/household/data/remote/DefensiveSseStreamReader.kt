package com.homelab.household.data.remote

import com.homelab.household.domain.model.ChatStreamEvent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefensiveSseStreamReader(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val controlTokens = listOf(
        "<|im_start|>",
        "<|im_end|>",
        "<|endoftext|>",
        "<|startoftext|>",
        "###",
        "---"
    )

    fun readEvents(channel: ByteReadChannel): Flow<ChatStreamEvent> = flow {
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed == "data: [DONE]" || trimmed == "[DONE]") {
                break
            }
            if (trimmed.startsWith("data:")) {
                val dataContent = trimmed.substringAfter("data:").trim()
                if (dataContent == "[DONE]") {
                    break
                }
                if (dataContent.startsWith("{")) {
                    try {
                        val element = json.parseToJsonElement(dataContent).jsonObject
                        val eventType = element["type"]?.jsonPrimitive?.content ?: ""

                        when (eventType) {
                            "delta" -> {
                                val rawContent = element["content"]?.jsonPrimitive?.content ?: ""
                                val sanitized = sanitizeContent(rawContent)
                                emit(ChatStreamEvent.Delta(sanitized))
                            }
                            "tool_executing" -> {
                                val tool = element["tool"]?.jsonPrimitive?.content ?: ""
                                emit(ChatStreamEvent.ToolExecuting(tool = tool))
                            }
                            "tool_result" -> {
                                val tool = element["tool"]?.jsonPrimitive?.content ?: ""
                                val success = element["success"]?.jsonPrimitive?.booleanOrNull ?: true
                                val error = element["error"]?.jsonPrimitive?.content
                                emit(ChatStreamEvent.ToolResult(tool = tool, success = success, error = error))
                            }
                            "tool_approval_proposal", "tool_proposal" -> {
                                val tool = element["tool"]?.jsonPrimitive?.content ?: ""
                                val message = element["message"]?.jsonPrimitive?.content ?: ""
                                emit(ChatStreamEvent.ToolApprovalProposal(tool = tool, message = message))
                            }
                            "done" -> {
                                val messageId = element["message_id"]?.jsonPrimitive?.content ?: ""
                                val assistantContent = sanitizeContent(element["assistant_content"]?.jsonPrimitive?.content ?: "")
                                val suggestSecret = element["suggest_secret_mode"]?.jsonPrimitive?.booleanOrNull ?: false
                                val isTurnSecret = element["is_turn_secret"]?.jsonPrimitive?.booleanOrNull ?: false
                                val agentName = element["agent_name"]?.jsonPrimitive?.content ?: ""
                                emit(
                                    ChatStreamEvent.Done(
                                        messageId = messageId,
                                        assistantContent = assistantContent,
                                        suggestSecretMode = suggestSecret,
                                        isTurnSecret = isTurnSecret,
                                        agentName = agentName
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore malformed line gracefully
                    }
                }
            }
        }
    }

    private fun sanitizeContent(content: String): String {
        var sanitized = content
        for (token in controlTokens) {
            sanitized = sanitized.replace(token, "")
        }
        return sanitized
    }
}
