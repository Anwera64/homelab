package com.homelab.household.app.screens.conversation

import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.EventMoment
import com.homelab.household.domain.model.ProposalDetails
import com.homelab.household.domain.model.ToolAction

/**
 * What an approval card shows about the write it asks for (canvas: ToolApproveRemove), from its
 * details: the event or note by its [title], [whenAt] for an event, and the first words of a note's
 * [preview]. Anything the model left out is simply not shown.
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

fun approvalCardDetails(card: AnswerPart.Proposal): ApprovalCardDetails =
    when (val details = card.details) {
        is ProposalDetails.CalendarEvent -> {
            ApprovalCardDetails(title = details.title, whenAt = details.start?.let { cardWhen(it, details.allDay) })
        }

        is ProposalDetails.Note -> {
            ApprovalCardDetails(title = details.title, preview = details.content?.let(::previewOf))
        }

        ProposalDetails.Other -> {
            ApprovalCardDetails(title = null)
        }
    }

/** A moment as the card says it; a whole day, or any moment of an all-day event, has no time. */
private fun cardWhen(
    moment: EventMoment,
    allDay: Boolean,
): CardWhen {
    val hour = moment.hour
    val minute = moment.minute
    val time = if (allDay || hour == null || minute == null) null else "${hour.pad()}:${minute.pad()}"
    return CardWhen(weekday = moment.weekday, day = moment.day, month = moment.month, time = time)
}

private fun Int.pad() = toString().padStart(2, '0')

private fun previewOf(content: String): String? {
    val line = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
    return if (line.length <= PREVIEW_LENGTH) line else line.take(PREVIEW_LENGTH).trimEnd() + "…"
}

private const val PREVIEW_LENGTH = 80
