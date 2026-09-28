package com.homelab.household.app.screens.conversation

import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolAction

/**
 * What an approval card shows about the write it asks for (canvas: ToolApproveRemove), read from
 * the arguments the model asked with: the event or note by its [title], [whenAt] for an event,
 * and the first words of a note's [preview]. Anything the model left out is simply not shown.
 */
data class ApprovalCardDetails(
    val title: String?,
    val whenAt: CardWhen? = null,
    val preview: String? = null,
    val repeat: CardRepeat? = null,
    val scope: CardScope? = null,
)

/** How often an event repeats, for the line under its date (canvas: RepeatAdd). */
enum class RepeatEvery { Day, Week, Month, Year }

/**
 * Every [interval] days, weeks, months or years; a weekly one on [weekdays] (0 for Monday); ending
 * on the day [until], after [count] times, or never.
 */
data class CardRepeat(
    val every: RepeatEvery,
    val interval: Int = 1,
    val weekdays: List<Int> = emptyList(),
    val until: CardWhen? = null,
    val count: Int? = null,
)

/**
 * Which dates of a repeating event a change or removal is for (canvas: RepeatRemoveA). Only on a
 * card about one date of a series; [code] is how the hub names it.
 */
enum class CardScope(
    val code: String,
) {
    OnlyThis("this"),
    ThisAndFollowing("following"),
}

/** What to send when approving: the member's pick of dates, only when it isn't what was proposed. */
fun scopeChange(
    proposed: CardScope?,
    chosen: CardScope?,
): Map<String, Any?>? = if (chosen != null && chosen != proposed) mapOf("scope" to chosen.code) else null

/**
 * A moment as the card says it: "Thu 11 Sep, 18:00", or "Sat 3 Oct · all day". [weekday] is 0 for
 * Monday, [month] 1 for January; [time] is null for a whole day.
 */
data class CardWhen(
    val weekday: Int,
    val day: Int,
    val month: Int,
    val time: String?,
)

/** How the card asks: the words on its main button, and whether saying yes loses something. */
enum class CardAsk {
    /** Decline / Approve. */
    Approve,

    /** Keep it / Remove, in the destructive style. */
    Remove,

    /** Keep it / Replace, in the destructive style: the note's words are gone once replaced. */
    Replace,
}

fun cardAsk(action: ToolAction?): CardAsk =
    when (action) {
        ToolAction.Delete -> CardAsk.Remove
        ToolAction.Replace -> CardAsk.Replace
        else -> CardAsk.Approve
    }

fun approvalCardDetails(card: AnswerPart.Proposal): ApprovalCardDetails {
    val arguments = card.arguments
    val title = (arguments["title"] as? String)?.trim()?.ifBlank { null }
    val start = arguments["start_time"] as? String
    val allDay = arguments["is_all_day"] == true
    val content = (arguments["content"] as? String)?.trim()?.ifBlank { null }
    val ofSeries = card.action in SERIES_ACTIONS && arguments["occurrence_start"] is String
    return ApprovalCardDetails(
        title = title,
        whenAt = start?.let { cardWhen(it, allDay) },
        preview = content?.let(::previewOf),
        repeat = cardRepeat(arguments["repeat"]),
        scope =
            if (!ofSeries) {
                null
            } else if (arguments["scope"] == CardScope.ThisAndFollowing.code) {
                CardScope.ThisAndFollowing
            } else {
                CardScope.OnlyThis
            },
    )
}

/** The repeat the model gave, or null when there is none or it is one the card has no words for. */
fun cardRepeat(value: Any?): CardRepeat? {
    val repeat = value as? Map<*, *> ?: return null
    val every = EVERY[repeat["frequency"]] ?: return null
    val weekdays =
        (repeat["days"] as? List<*>).orEmpty().map { day ->
            WEEKDAY_CODES.indexOf(day).takeIf { it >= 0 } ?: return null
        }
    return CardRepeat(
        every = every,
        interval = (repeat["interval"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 1,
        weekdays = weekdays.sorted(),
        until = (repeat["until"] as? String)?.let { cardWhen(it.take(DATE_LENGTH), allDay = true) },
        count = (repeat["count"] as? Number)?.toInt()?.takeIf { it > 0 },
    )
}

/**
 * The moment an ISO timestamp names, as it was written: "2026-09-11T18:00:00" is 18:00 on the
 * 11th, whatever the phone's zone, because the model wrote it in the member's own time. A date on
 * its own is a whole day. Anything else is not shown rather than guessed at.
 */
fun cardWhen(
    iso: String,
    allDay: Boolean = false,
): CardWhen? {
    val match = ISO.matchEntire(iso.trim()) ?: return null
    val (y, m, d) = match.destructured
    val year = y.toInt()
    val month = m.toInt()
    val day = d.toInt()
    if (month !in 1..12 || day !in 1..31) return null
    val hour = match.groups[HOUR]?.value
    val minute = match.groups[MINUTE]?.value
    val time = if (allDay || hour == null || minute == null) null else "$hour:$minute"
    return CardWhen(weekday = weekday(year, month, day), day = day, month = month, time = time)
}

/** 0 for Monday: Sakamoto's method, since the phone has no calendar library to ask. */
private fun weekday(
    year: Int,
    month: Int,
    day: Int,
): Int {
    val y = if (month < 3) year - 1 else year
    val sunday0 = (y + y / 4 - y / 100 + y / 400 + MONTH_OFFSETS[month - 1] + day) % 7
    return (sunday0 + 6) % 7
}

private fun previewOf(content: String): String {
    val line = content.lineSequence().first { it.isNotBlank() }.trim()
    return if (line.length <= PREVIEW_LENGTH) line else line.take(PREVIEW_LENGTH).trimEnd() + "…"
}

private val SERIES_ACTIONS = setOf(ToolAction.Update, ToolAction.Delete)
private val EVERY =
    mapOf(
        "daily" to RepeatEvery.Day,
        "weekly" to RepeatEvery.Week,
        "monthly" to RepeatEvery.Month,
        "yearly" to RepeatEvery.Year,
    )
private val WEEKDAY_CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
private const val DATE_LENGTH = 10
private val ISO =
    Regex("""(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:?\d{2})?)?""")
private const val HOUR = 4
private const val MINUTE = 5
private val MONTH_OFFSETS = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
private const val PREVIEW_LENGTH = 80
