package com.homelab.household.data.mapper

import com.homelab.household.domain.model.AnswerPart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
                if (tool != null && success != null) {
                    if (success) AnswerPart.ToolDone(tool) else AnswerPart.ToolFailed(tool)
                } else {
                    null
                }
            }

            else -> {
                null
            }
        }
}
