package com.homelab.household.domain.model

/**
 * A detail of a write the member can change before approving it (canvas: ToolEdit): an event's
 * [What], [Day] and [Time], or a note's title ([What]) and [Words].
 */
enum class EditField { What, Day, Time, Words }

/** A day of the year as typed on a card, before it is given its year. [month] is 1 for January. */
data class CardDay(
    val day: Int,
    val month: Int,
)

/** A time of day as typed on a card. */
data class TimeOfDay(
    val hour: Int,
    val minute: Int,
)

/**
 * What approving with the member's edits sends: only the details that changed in [edited], or null
 * when nothing did. A field in [invalid] was left empty or can't be read, and while there is one
 * there is no edit to send.
 */
data class ProposalEdit(
    val edited: ProposalDetails?,
    val invalid: Set<EditField>,
)

/**
 * The fields Edit opens on [card], in the order they are drawn, or none when it can't be edited.
 *
 * A removal and a replacement are never edited: saying yes to losing something should be the whole
 * question. Adding to a note keeps its title, which says which note it goes into.
 */
fun editableFields(card: AnswerPart.Proposal): List<EditField> {
    if (card.action == ToolAction.Delete || card.action == ToolAction.Replace) return emptyList()
    return when (val details = card.details) {
        is ProposalDetails.CalendarEvent -> {
            buildList {
                add(EditField.What)
                if (details.start != null) add(EditField.Day)
                if (details.start?.isWholeDay == false && !details.allDay) add(EditField.Time)
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

/**
 * What [field] starts as: the proposed title, words, or time as "HH:MM". [EditField.Day] is left to
 * the screen, which words days in the member's language.
 */
fun proposedText(
    card: AnswerPart.Proposal,
    field: EditField,
): String =
    when (field) {
        EditField.What -> titleOf(card.details)?.trim().orEmpty()
        EditField.Words -> (card.details as? ProposalDetails.Note)?.content.orEmpty()
        EditField.Time -> startOf(card)?.timeOfDay()?.written().orEmpty()
        EditField.Day -> ""
    }

/** Whether [text] in [field] says something other than what was proposed. */
fun isChanged(
    card: AnswerPart.Proposal,
    field: EditField,
    text: String,
): Boolean {
    val start = startOf(card)
    return when (field) {
        EditField.What -> text.trim() != proposedText(card, field)
        EditField.Words -> text != proposedText(card, field)
        EditField.Day -> start != null && parseDay(text) != CardDay(start.day, start.month)
        EditField.Time -> start != null && parseTime(text) != start.timeOfDay()
    }
}

/** Whether [text] can go in [field]: nothing empty, and a day or a time that reads as one. */
fun isValid(
    field: EditField,
    text: String,
): Boolean =
    when (field) {
        EditField.What, EditField.Words -> text.isNotBlank()
        EditField.Day -> parseDay(text) != null
        EditField.Time -> parseTime(text) != null
    }

/**
 * The edit approving [card] with [texts] makes, keyed by field. A field missing from [texts] is as
 * proposed.
 *
 * A new day or time moves the start, keeping its zone, and the end moves with it so the event keeps
 * its length. A day is given the year that puts it nearest the proposed one, so "2 Jan" for a New
 * Year's Eve event is the next January.
 */
fun editProposal(
    card: AnswerPart.Proposal,
    texts: Map<EditField, String>,
): ProposalEdit {
    val invalid = texts.filter { (field, text) -> !isValid(field, text) }.keys
    if (invalid.isNotEmpty()) return ProposalEdit(edited = null, invalid = invalid)
    val changed = texts.filter { (field, text) -> isChanged(card, field, text) }
    if (changed.isEmpty()) return ProposalEdit(edited = null, invalid = emptySet())
    val title = changed[EditField.What]?.trim()

    val edited =
        when (val details = card.details) {
            is ProposalDetails.CalendarEvent -> {
                val start = details.start
                if (start != null && (EditField.Day in changed || EditField.Time in changed)) {
                    val day = changed[EditField.Day]?.let(::parseDay) ?: CardDay(start.day, start.month)
                    // 29 February with no leap year near enough to hold it.
                    val year =
                        nearestYear(start, day)
                            ?: return ProposalEdit(edited = null, invalid = setOf(EditField.Day))
                    val time = changed[EditField.Time]?.let(::parseTime) ?: start.timeOfDay()
                    val moved =
                        start.copy(
                            year = year,
                            month = day.month,
                            day = day.day,
                            hour = time?.hour,
                            minute = time?.minute,
                        )
                    val shift = moved.minutes() - start.minutes()
                    details.copy(title = title, start = moved, end = details.end?.plusMinutes(shift))
                } else {
                    details.copy(title = title, start = null, end = null)
                }
            }

            is ProposalDetails.Note -> {
                ProposalDetails.Note(title = title, content = changed[EditField.Words])
            }

            ProposalDetails.Other -> {
                return ProposalEdit(edited = null, invalid = emptySet())
            }
        }
    return ProposalEdit(edited = edited, invalid = emptySet())
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

/**
 * A day as the card writes it or as people type it: "Sat 12 Sep", "12 september", "Sep 12", "12/9"
 * (day first) or "2026-09-12". A weekday is allowed and ignored; the date decides.
 */
fun parseDay(text: String): CardDay? {
    val words = text.trim().lowercase()
    ISO_DATE.matchEntire(words)?.let { m -> return cardDay(m.groupValues[3].toInt(), m.groupValues[2].toInt()) }
    SLASHED.matchEntire(words)?.let { m -> return cardDay(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
    val tokens = words.split(' ', ',').filter { it.isNotEmpty() }
    val number = tokens.singleOrNull { it.all(Char::isDigit) }?.toIntOrNull() ?: return null
    val names = tokens.filterNot { it.all(Char::isDigit) }
    val month = names.lastOrNull()?.let(::monthNamed) ?: return null
    // Anything before the month may only be a weekday.
    if (names.size > 2 || (names.size == 2 && names.first().take(3) !in WEEKDAY_NAMES)) return null
    return cardDay(number, month)
}

/** A time as people type it: "20:30", "8.30", "0705" or a bare hour, "20". */
fun parseTime(text: String): TimeOfDay? {
    val m = TIME.matchEntire(text.trim()) ?: return null
    val hour = m.groupValues[1].toInt()
    val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
    if (hour !in 0 until HOURS_PER_DAY || minute !in 0 until MINUTES_PER_HOUR) return null
    return TimeOfDay(hour, minute)
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

private fun TimeOfDay.written(): String = "${hour.pad()}:${minute.pad()}"

private fun EventMoment.minutes(): Long =
    daysFromCivil(year, month, day) * MINUTES_PER_DAY + (hour ?: 0) * MINUTES_PER_HOUR + (minute ?: 0)

private fun EventMoment.plusMinutes(delta: Long): EventMoment {
    val total = minutes() + delta
    val (y, m, d) = civilFromDays(total.floorDiv(MINUTES_PER_DAY))
    if (isWholeDay) return copy(year = y, month = m, day = d)
    val inDay = total.mod(MINUTES_PER_DAY).toInt()
    return copy(year = y, month = m, day = d, hour = inDay / MINUTES_PER_HOUR, minute = inDay % MINUTES_PER_HOUR)
}

/** Of the proposed year and the ones either side, the one that puts [day] nearest [start]. */
private fun nearestYear(
    start: EventMoment,
    day: CardDay,
): Int? {
    val proposed = daysFromCivil(start.year, start.month, start.day)
    return (start.year - 1..start.year + 1)
        .filter { day.day <= daysIn(it, day.month) }
        .minByOrNull { kotlin.math.abs(daysFromCivil(it, day.month, day.day) - proposed) }
}

private fun cardDay(
    day: Int,
    month: Int,
): CardDay? =
    // 29 February is let through: it exists in the year it will be given, or no year will take it.
    if (month in 1..MONTHS && day in 1..daysIn(LEAP_YEAR, month)) CardDay(day, month) else null

private fun monthNamed(word: String): Int? =
    if (word.length < NAME_LENGTH) {
        null
    } else {
        MONTH_NAMES.indexOfFirst { word.startsWith(it) }.takeIf { it >= 0 }?.plus(1)
    }

private fun daysIn(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
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

private fun Int.pad(): String = toString().padStart(2, '0')

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24L * 60
private const val MONTHS = 12
private const val NAME_LENGTH = 3
private const val LEAP_YEAR = 2028
private val ISO_DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
private val SLASHED = Regex("""(\d{1,2})/(\d{1,2})""")
private val TIME = Regex("""(\d{1,2})(?:[:.h]?(\d{2}))?""")
private val MONTH_NAMES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
private val WEEKDAY_NAMES = setOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
