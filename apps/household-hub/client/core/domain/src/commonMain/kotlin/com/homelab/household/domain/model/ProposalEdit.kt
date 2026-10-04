package com.homelab.household.domain.model

/**
 * A detail of a write the member can change before approving it (canvas: ToolEdit): an event's
 * [What], [Day] and [Time], how it [Repeat]s and when that [Ends], or a note's title ([What]) and
 * [Words].
 */
enum class EditField { What, Day, Time, Words, Repeat, Ends }

/** A calendar day picked on a card. [month] is 1 for January. */
data class EventDate(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    /** Days since 1970-01-01, which is how a date picker counts. */
    fun toEpochDay(): Long = daysFromCivil(year, month, day)

    companion object {
        fun fromEpochDay(days: Long): EventDate {
            val (year, month, day) = civilFromDays(days)
            return EventDate(year, month, day)
        }
    }
}

/** A time of day picked on a card. */
data class TimeOfDay(
    val hour: Int,
    val minute: Int,
)

/**
 * Whether a calendar write's rule can be changed on its card: an add, or a change to a one-off, to
 * the whole series, or to this date and the ones after it, but not to one date on its own, which
 * can't have a rule of its own. A rule the fields have no words for (yearly, every other week) stays
 * as proposed rather than be rewritten.
 */
private fun opensRepeat(
    action: ToolAction?,
    details: ProposalDetails.CalendarEvent,
    scope: EventScope?,
): Boolean {
    val rule = details.repeat
    val showable = rule == null || (rule.interval == 1 && rule.every != RepeatEvery.Year)
    val writes = action == ToolAction.Create || (action == ToolAction.Update && scope != EventScope.OnlyThis)
    return showable && writes
}

/**
 * What a card's fields hold while it is edited. [day] and [time] are null where the card has no such
 * field: a note, an event with no start, a whole day.
 */
data class EditValues(
    val title: String,
    val words: String,
    val day: EventDate?,
    val time: TimeOfDay?,
)

/**
 * What approving with the member's edits sends: only the details that changed in [edited], or null
 * when nothing did. [changed] are the fields that no longer say what was proposed. A field in
 * [invalid] was left empty, and while there is one there is no edit to send.
 */
data class ProposalEdit(
    val edited: ProposalDetails?,
    val changed: Set<EditField>,
    val invalid: Set<EditField>,
)

/** Whether [card]'s Repeat offers None: a series can't be made a one-off again. */
fun offersNoRepeat(card: AnswerPart.Proposal): Boolean =
    card.action == ToolAction.Create || (card.details as? ProposalDetails.CalendarEvent)?.repeat == null

/**
 * The fields Edit opens on [card], in the order they are drawn, or none when it can't be edited.
 *
 * A removal and a replacement are never edited: saying yes to losing something should be the whole
 * question. Adding to a note keeps its title, which says which note it goes into.
 */
fun editableFields(
    card: AnswerPart.Proposal,
    scope: EventScope? = (card.details as? ProposalDetails.CalendarEvent)?.scope,
): List<EditField> {
    if (card.action == ToolAction.Delete || card.action == ToolAction.Replace) return emptyList()
    return when (val details = card.details) {
        is ProposalDetails.CalendarEvent -> {
            buildList {
                add(EditField.What)
                if (details.start != null) add(EditField.Day)
                if (details.start?.isWholeDay == false && !details.allDay) add(EditField.Time)
                if (opensRepeat(card.action, details, scope)) {
                    add(EditField.Repeat)
                    add(EditField.Ends)
                }
            }
        }

        is ProposalDetails.Note -> {
            if (card.action == ToolAction.Append) listOf(EditField.Words) else listOf(EditField.What, EditField.Words)
        }

        ProposalDetails.Other -> {
            emptyList()
        }
    }
}

/** What [card]'s fields start as: the details as proposed. */
fun proposedValues(card: AnswerPart.Proposal): EditValues {
    val start = startOf(card)
    return EditValues(
        title = titleOf(card.details)?.trim().orEmpty(),
        words = (card.details as? ProposalDetails.Note)?.content.orEmpty(),
        day = start?.let { EventDate(it.year, it.month, it.day) },
        time = start?.timeOfDay(),
    )
}

/**
 * The edit approving [card] with [values] in its fields makes.
 *
 * A new day or time moves the start, keeping its zone, and the end moves with it so the event keeps
 * its length.
 */
fun editProposal(
    card: AnswerPart.Proposal,
    values: EditValues,
): ProposalEdit {
    val proposed = proposedValues(card)
    val fields = editableFields(card)
    val changed =
        buildSet {
            if (values.title.trim() != proposed.title) add(EditField.What)
            if (values.words != proposed.words) add(EditField.Words)
            if (values.day != proposed.day) add(EditField.Day)
            if (values.time != proposed.time) add(EditField.Time)
        }.intersect(fields.toSet())
    val invalid =
        buildSet {
            if (EditField.What in fields && values.title.isBlank()) add(EditField.What)
            if (EditField.Words in fields && values.words.isBlank()) add(EditField.Words)
        }
    if (invalid.isNotEmpty() ||
        changed.isEmpty()
    ) {
        return ProposalEdit(edited = null, changed = changed, invalid = invalid)
    }
    val title = values.title.trim().takeIf { EditField.What in changed }

    val edited =
        when (val details = card.details) {
            is ProposalDetails.CalendarEvent -> {
                val start = details.start
                val day = values.day
                if (start != null && day != null && (EditField.Day in changed || EditField.Time in changed)) {
                    val moved =
                        start.copy(
                            year = day.year,
                            month = day.month,
                            day = day.day,
                            hour = values.time?.hour,
                            minute = values.time?.minute,
                        )
                    val shift = moved.minutes() - start.minutes()
                    details.copy(title = title, start = moved, end = details.end?.plusMinutes(shift))
                } else {
                    details.copy(title = title, start = null, end = null)
                }
            }

            is ProposalDetails.Note -> {
                ProposalDetails.Note(title = title, content = values.words.takeIf { EditField.Words in changed })
            }

            ProposalDetails.Other -> {
                null
            }
        }
    return ProposalEdit(edited = edited, changed = changed, invalid = emptySet())
}

/** These details with an [edit] laid over them: what the edit has replaces what was proposed. */
fun ProposalDetails.withEdit(edit: ProposalDetails): ProposalDetails =
    when {
        this is ProposalDetails.CalendarEvent && edit is ProposalDetails.CalendarEvent -> {
            copy(title = edit.title ?: title, start = edit.start ?: start, end = edit.end ?: end)
        }

        this is ProposalDetails.Note && edit is ProposalDetails.Note -> {
            copy(title = edit.title ?: title, content = edit.content ?: content)
        }

        else -> {
            this
        }
    }

private fun titleOf(details: ProposalDetails): String? =
    when (details) {
        is ProposalDetails.CalendarEvent -> details.title
        is ProposalDetails.Note -> details.title
        ProposalDetails.Other -> null
    }

private fun startOf(card: AnswerPart.Proposal): EventMoment? = (card.details as? ProposalDetails.CalendarEvent)?.start

private fun EventMoment.timeOfDay(): TimeOfDay? {
    val h = hour ?: return null
    val m = minute ?: return null
    return TimeOfDay(h, m)
}

private fun EventMoment.minutes(): Long =
    daysFromCivil(year, month, day) * MINUTES_PER_DAY + (hour ?: 0) * MINUTES_PER_HOUR + (minute ?: 0)

private fun EventMoment.plusMinutes(delta: Long): EventMoment {
    val total = minutes() + delta
    val (y, m, d) = civilFromDays(total.floorDiv(MINUTES_PER_DAY))
    if (isWholeDay) return copy(year = y, month = m, day = d)
    val inDay = total.mod(MINUTES_PER_DAY).toInt()
    return copy(year = y, month = m, day = d, hour = inDay / MINUTES_PER_HOUR, minute = inDay % MINUTES_PER_HOUR)
}

/** Days since 1970-01-01, and back: Howard Hinnant's civil calendar, since there is no date library. */
private fun daysFromCivil(
    year: Int,
    month: Int,
    day: Int,
): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = y.floorDiv(400L)
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
    val z = days + 719468
    val era = z.floorDiv(146097L)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
    return Triple(year, month, day)
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24L * 60
