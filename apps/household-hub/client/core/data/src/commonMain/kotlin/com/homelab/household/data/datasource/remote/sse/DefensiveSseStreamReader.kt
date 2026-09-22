package com.homelab.household.data.datasource.remote.sse

import com.homelab.household.domain.model.ChatStreamEvent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefensiveSseStreamReader(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val controlTokens =
        listOf(
            "<|im_start|>",
            "<|im_end|>",
            "<|endoftext|>",
            "<|startoftext|>",
            "###",
            "---",
        )

    fun readEvents(channel: ByteReadChannel): Flow<ChatStreamEvent> =
        flow {
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
                        // Only the parse is defensive. Emission is deliberately kept outside the catch
                        // so a downstream collector failure can never be mistaken for a malformed line.
                        val event =
                            try {
                                parseEvent(dataContent)
                            } catch (_: IllegalArgumentException) {
                                // Malformed or unexpectedly shaped JSON: skip the line, keep streaming.
                                // SerializationException is an IllegalArgumentException, and so is a shape
                                // mismatch from jsonObject/jsonPrimitive: those call kotlinx's own private
                                // error(JsonElement, String) helper, which throws IllegalArgumentException —
                                // not kotlin.error(), which would be an IllegalStateException. A reviewer
                                // read it the other way once; the shape test below pins which it is.
                                null
                            }
                        if (event != null) {
                            emit(event)
                        }
                    }
                }
            }
        }

    /** Pure parse step: returns `null` for event types this client does not model. */
    private fun parseEvent(dataContent: String): ChatStreamEvent? {
        val element = json.parseToJsonElement(dataContent).jsonObject
        return when (element["type"]?.jsonPrimitive?.content ?: "") {
            "delta" -> {
                val rawContent = element["content"]?.jsonPrimitive?.content ?: ""
                ChatStreamEvent.Delta(sanitizeContent(rawContent))
            }

            "tool_executing" -> {
                val tool = element["tool"]?.jsonPrimitive?.content ?: ""
                ChatStreamEvent.ToolExecuting(tool = tool)
            }

            "tool_result" -> {
                val tool = element["tool"]?.jsonPrimitive?.content ?: ""
                val success = element["success"]?.jsonPrimitive?.booleanOrNull ?: true
                val error = element["error"]?.jsonPrimitive?.content
                ChatStreamEvent.ToolResult(tool = tool, success = success, error = error)
            }

            "tool_approval_proposal", "tool_proposal" -> {
                val tool = element["tool"]?.jsonPrimitive?.content ?: ""
                val message = element["message"]?.jsonPrimitive?.content ?: ""
                ChatStreamEvent.ToolApprovalProposal(tool = tool, message = message)
            }

            // The two ways the hub says a turn went wrong. Dropping these as unknown types is
            // what left a failed turn looking exactly like one that had not started yet.
            "turn_failed" -> {
                ChatStreamEvent.TurnFailed
            }

            "error" -> {
                val message = element["error"]?.jsonPrimitive?.content ?: ""
                ChatStreamEvent.StreamError(message)
            }

            "done" -> {
                val messageId = element["message_id"]?.jsonPrimitive?.content ?: ""
                val assistantContent = sanitizeContent(element["assistant_content"]?.jsonPrimitive?.content ?: "")
                val suggestSecret = element["suggest_secret_mode"]?.jsonPrimitive?.booleanOrNull ?: false
                val isTurnSecret = element["is_turn_secret"]?.jsonPrimitive?.booleanOrNull ?: false
                val agentName = element["agent_name"]?.jsonPrimitive?.content ?: ""
                ChatStreamEvent.Done(
                    messageId = messageId,
                    assistantContent = assistantContent,
                    suggestSecretMode = suggestSecret,
                    isTurnSecret = isTurnSecret,
                    agentName = agentName,
                )
            }

            else -> {
                null
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
