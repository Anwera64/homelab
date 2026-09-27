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
)

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
    return ApprovalCardDetails(
        title = title,
        whenAt = start?.let { cardWhen(it, allDay) },
        preview = content?.let(::previewOf),
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

private val ISO =
    Regex("""(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:?\d{2})?)?""")
private const val HOUR = 4
private const val MINUTE = 5
private val MONTH_OFFSETS = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
private const val PREVIEW_LENGTH = 80
