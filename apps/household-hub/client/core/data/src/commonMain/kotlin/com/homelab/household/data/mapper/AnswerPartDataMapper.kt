package com.homelab.household.data.mapper

import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ProposalStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns the hub's `parts` array into [AnswerPart]s.
 *
 * An older hub never sent `parts` at all, and any hub can send a part type this phone does not
 * know yet: both cases give nothing rather than fail the whole message, so one bad or unfamiliar
 * entry never costs the rest of the answer.
 */
object AnswerPartDataMapper {
    fun fromJson(element: JsonElement?): List<AnswerPart> =
        (element as? JsonArray).orEmpty().mapNotNull { part -> (part as? JsonObject)?.let(::partFromJson) }

    private fun partFromJson(part: JsonObject): AnswerPart? =
        when (part["type"]?.jsonPrimitive?.contentOrNull) {
            "text" -> {
                part["content"]?.jsonPrimitive?.contentOrNull?.let { AnswerPart.Text(it) }
            }

            "thought" -> {
                part["seconds"]?.jsonPrimitive?.intOrNull?.let { AnswerPart.Thought(it) }
            }

            "tool" -> {
                val tool = part["tool"]?.jsonPrimitive?.contentOrNull
                val success = part["success"]?.jsonPrimitive?.booleanOrNull
                val summary = ToolSummaryDataMapper.fromJson(part["summary"])
                val automatic = (part["auto"] as? JsonPrimitive)?.booleanOrNull ?: false
                if (tool != null && success != null) {
                    if (success) AnswerPart.ToolDone(tool, summary, automatic) else AnswerPart.ToolFailed(tool, summary)
                } else {
                    null
                }
            }

            "proposal" -> {
                val toolCallId = part["tool_call_id"]?.jsonPrimitive?.contentOrNull
                val tool = part["tool"]?.jsonPrimitive?.contentOrNull
                if (toolCallId != null && tool != null) {
                    AnswerPart.Proposal(
                        toolCallId = toolCallId,
                        tool = tool,
                        action =
                            (part["action"] as? JsonPrimitive)?.contentOrNull?.let(
                                ToolSummaryDataMapper::actionFromCode,
                            ),
                        details = ProposalDetailsDataMapper.fromJson(tool, part["arguments"]),
                        status = statusFromCode((part["status"] as? JsonPrimitive)?.contentOrNull),
                    )
                } else {
                    null
                }
            }

            "declined" -> {
                part["tool"]?.jsonPrimitive?.contentOrNull?.let { tool ->
                    AnswerPart.Declined(tool, ToolSummaryDataMapper.fromJson(part["summary"]))
                }
            }

            else -> {
                null
            }
        }

    /** A card this phone can't place is shown as waiting: asking again is safer than assuming. */
    private fun statusFromCode(code: String?): ProposalStatus =
        when (code) {
            "approved" -> ProposalStatus.Approved
            "declined" -> ProposalStatus.Declined
            else -> ProposalStatus.Pending
        }
}
