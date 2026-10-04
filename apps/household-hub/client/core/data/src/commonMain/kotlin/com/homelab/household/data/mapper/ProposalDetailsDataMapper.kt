package com.homelab.household.data.mapper

import com.homelab.household.domain.model.EventMoment
import com.homelab.household.domain.model.EventRepeat
import com.homelab.household.domain.model.EventScope
import com.homelab.household.domain.model.HubTool
import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.domain.model.RepeatChange
import com.homelab.household.domain.model.RepeatEvery
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * A write's arguments, as the model asked for them, to and from [ProposalDetails]. This is the one
 * place that knows the hub's argument names and how the model writes a moment; what it can't read
 * it leaves out rather than guesses at.
 */
object ProposalDetailsDataMapper {
    fun fromJson(
        tool: String,
        element: JsonElement?,
    ): ProposalDetails {
        val arguments = element as? JsonObject ?: JsonObject(emptyMap())
        return when (tool) {
            HubTool.CALENDAR_WRITE -> {
                val allDay = arguments.boolean(IS_ALL_DAY) == true
                ProposalDetails.CalendarEvent(
                    title = arguments.text(TITLE)?.trim()?.ifBlank { null },
                    start = arguments.text(START_TIME)?.let { momentOf(it, allDay) },
                    end = arguments.text(END_TIME)?.let { momentOf(it, allDay) },
                    allDay = allDay,
                    repeat = (arguments[REPEAT] as? JsonObject)?.let(::repeatOf),
                    scope = scopeOf(arguments),
                )
            }

            HubTool.DOCUMENT_WRITER -> {
                ProposalDetails.Note(
                    title = arguments.text(TITLE)?.trim()?.ifBlank { null },
                    content = arguments.text(CONTENT)?.ifBlank { null },
                )
            }

            else -> {
                ProposalDetails.Other
            }
        }
    }

    /** Details changed on a card, in the hub's names. Only what they have is sent. */
    fun toArguments(details: ProposalDetails): JsonObject =
        buildJsonObject {
            when (details) {
                is ProposalDetails.CalendarEvent -> {
                    details.title?.let { put(TITLE, it) }
                    details.start?.let { put(START_TIME, isoOf(it, details.allDay)) }
                    details.end?.let { put(END_TIME, isoOf(it, details.allDay)) }
                    put(IS_ALL_DAY, details.allDay)
                    details.scope?.let { put(SCOPE, if (it == EventScope.ThisAndFollowing) FOLLOWING else ONLY_THIS) }
                    details.repeatChange?.let { put(REPEAT, repeatJson(it)) }
                }

                is ProposalDetails.Note -> {
                    details.title?.let { put(TITLE, it) }
                    details.content?.let { put(CONTENT, it) }
                }

                ProposalDetails.Other -> {
                    Unit
                }
            }
        }

    /**
     * The moment an ISO timestamp names, as it was written: "2026-09-11T18:00:00" is 18:00 on the
     * 11th, whatever the phone's zone, because the model wrote it in the member's own time. A date on
     * its own, or any time of an all-day event, is a whole day.
     */
    private fun momentOf(
        iso: String,
        allDay: Boolean,
    ): EventMoment? {
        val match = ISO.matchEntire(iso.trim()) ?: return null
        val (year, month, day) = match.destructured
        if (month.toInt() !in 1..MONTHS || day.toInt() !in 1..MAX_DAY) return null
        val hour = match.groups[HOUR]?.value?.toInt()
        val minute = match.groups[MINUTE]?.value?.toInt()
        val timed = !allDay && hour != null && minute != null
        return EventMoment(
            year = year.toInt(),
            month = month.toInt(),
            day = day.toInt(),
            hour = if (timed) hour else null,
            minute = if (timed) minute else null,
            offset = if (timed) match.groups[OFFSET]?.value else null,
        )
    }

    /**
     * How an event repeats, from the hub's `repeat` object. A rule the card has no words for (hourly,
     * a day it can't read, no interval) is read as not repeating rather than shown wrong.
     */
    private fun repeatOf(repeat: JsonObject): EventRepeat? {
        val every = FREQUENCIES[repeat.text(FREQUENCY)?.lowercase()] ?: return null
        val interval = repeat.int(INTERVAL) ?: 1
        if (interval < 1) return null
        val weekdays =
            (repeat[DAYS] as? JsonArray).orEmpty().map { day ->
                val code = (day as? JsonPrimitive)?.takeIf { it.isString }?.content?.uppercase()
                WEEKDAYS.indexOf(code).takeIf { it >= 0 } ?: return null
            }
        return EventRepeat(
            every = every,
            interval = interval,
            weekdays = weekdays,
            until = repeat.text(UNTIL)?.let { momentOf(it, allDay = true) },
            count = repeat.int(COUNT)?.takeIf { it > 0 },
        )
    }

    /**
     * Which dates a change is for: only a write naming one date of a series (`occurrence_start`) has
     * a choice, and the hub takes it to be that one date unless it says `following`. A write for the
     * whole series (`all`) has no choice either, even when the model names a date beside it: the hub
     * would change all of it whatever the card's switch said.
     */
    private fun scopeOf(arguments: JsonObject): EventScope? {
        val scope = arguments.text(SCOPE)
        if (scope == ALL || arguments.text(OCCURRENCE_START).isNullOrBlank()) return null
        return if (scope == FOLLOWING) EventScope.ThisAndFollowing else EventScope.OnlyThis
    }

    /** The member's new rule as the hub's `repeat`: the whole of it, or `none` to stop repeating. */
    private fun repeatJson(change: RepeatChange): JsonObject =
        buildJsonObject {
            when (change) {
                RepeatChange.Stop -> {
                    put(FREQUENCY, NONE)
                }

                is RepeatChange.To -> {
                    val rule = change.rule
                    put(FREQUENCY, FREQUENCIES.entries.first { it.value == rule.every }.key)
                    put(INTERVAL, rule.interval)
                    if (rule.weekdays.isNotEmpty()) {
                        put(
                            DAYS,
                            JsonArray(rule.weekdays.map { JsonPrimitive(WEEKDAYS[it]) }),
                        )
                    }
                    rule.until?.let { put(UNTIL, isoOf(it, allDay = true)) }
                    rule.count?.let { put(COUNT, it) }
                }
            }
        }

    private fun isoOf(
        moment: EventMoment,
        allDay: Boolean,
    ): String {
        val date = "${moment.year.pad(YEAR_DIGITS)}-${moment.month.pad()}-${moment.day.pad()}"
        val hour = moment.hour
        val minute = moment.minute
        if (allDay || hour == null || minute == null) return date
        return "${date}T${hour.pad()}:${minute.pad()}:00${moment.offset.orEmpty()}"
    }

    private fun Int.pad(digits: Int = 2) = toString().padStart(digits, '0')

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private const val TITLE = "title"
    private const val CONTENT = "content"
    private const val START_TIME = "start_time"
    private const val END_TIME = "end_time"
    private const val IS_ALL_DAY = "is_all_day"
    private const val REPEAT = "repeat"
    private const val OCCURRENCE_START = "occurrence_start"
    private const val SCOPE = "scope"
    private const val FOLLOWING = "following"
    private const val ALL = "all"
    private const val ONLY_THIS = "this"
    private const val FREQUENCY = "frequency"
    private const val INTERVAL = "interval"
    private const val DAYS = "days"
    private const val UNTIL = "until"
    private const val COUNT = "count"
    private val FREQUENCIES =
        mapOf(
            "daily" to RepeatEvery.Day,
            "weekly" to RepeatEvery.Week,
            "monthly" to RepeatEvery.Month,
            "yearly" to RepeatEvery.Year,
        )
    private const val NONE = "none"
    private val WEEKDAYS = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    private const val MONTHS = 12
    private const val MAX_DAY = 31
    private const val YEAR_DIGITS = 4
    private const val HOUR = 4
    private const val MINUTE = 5
    private const val OFFSET = 6
    private val ISO =
        Regex("""(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?)?""")
}
