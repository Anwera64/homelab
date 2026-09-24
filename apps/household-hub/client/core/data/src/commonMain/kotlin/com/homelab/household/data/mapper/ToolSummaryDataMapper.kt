package com.homelab.household.data.mapper

import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSource
import com.homelab.household.domain.model.ToolSummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Turns a tool part's `summary` into a [ToolSummary], for a saved answer and a live one alike.
 *
 * Anything missing or mis-shaped is left out rather than failing the part: a step without its
 * summary still draws, just as it did before the hub sent one. Only web links are kept, because a
 * source is something a person taps, and search results are whatever the web sent back.
 */
object ToolSummaryDataMapper {
    fun fromJson(element: JsonElement?): ToolSummary? {
        val summary = element as? JsonObject ?: return null
        return ToolSummary(
            query = summary.string("query"),
            count = (summary["count"] as? JsonPrimitive)?.intOrNull,
            sources = (summary["sources"] as? JsonArray).orEmpty().mapNotNull(::sourceFromJson),
            reason = summary.string("reason")?.let(::reasonFromCode),
            title = summary.string("title"),
        )
    }

    private fun sourceFromJson(element: JsonElement): ToolSource? {
        val source = element as? JsonObject ?: return null
        val url = source.string("url") ?: return null
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) return null
        return ToolSource(title = source.string("title").orEmpty(), url = url)
    }

    private fun reasonFromCode(code: String): ToolFailureReason =
        when (code) {
            "service_unavailable" -> ToolFailureReason.ServiceUnavailable
            "blocked" -> ToolFailureReason.Blocked
            "forbidden" -> ToolFailureReason.Forbidden
            "too_large" -> ToolFailureReason.TooLarge
            "not_a_page" -> ToolFailureReason.NotAPage
            "unreadable" -> ToolFailureReason.Unreadable
            "not_found" -> ToolFailureReason.NotFound
            else -> ToolFailureReason.Unknown
        }

    private fun JsonObject.string(key: String): String? {
        val value = this[key] as? JsonPrimitive ?: return null
        return if (value.isString) value.contentOrNull else null
    }
}
