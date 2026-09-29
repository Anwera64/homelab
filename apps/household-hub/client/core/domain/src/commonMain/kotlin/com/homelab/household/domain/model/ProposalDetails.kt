package com.homelab.household.domain.model

/**
 * What a write on an approval card is about, made sense of by the data layer: the event or note it
 * adds, changes or removes. The hub's argument names never get past the data mapper, so the card and
 * its edits work on these, and anything the model left out or wrote in a way the phone can't read
 * is null rather than guessed at.
 */
sealed interface ProposalDetails {
    /** An event on the member's calendar. [start] and [end] have no time when it is [allDay]. */
    data class CalendarEvent(
        val title: String?,
        val start: EventMoment?,
        val end: EventMoment?,
        val allDay: Boolean,
    ) : ProposalDetails

    /** A note: its [title], and the [content] it is given or gains. */
    data class Note(
        val title: String?,
        val content: String?,
    ) : ProposalDetails

    /** A write this phone has no card words for yet. */
    data object Other : ProposalDetails
}

/**
 * A moment as the model wrote it, in the member's own time: 18:00 on the 11th is 18:00 whatever the
 * phone's zone. [month] is 1 for January. [hour] and [minute] are null for a whole day. [offset] is
 * the zone the model wrote after the time, if any ("Z", "+02:00"), kept so an edit sends it back.
 */
data class EventMoment(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int? = null,
    val minute: Int? = null,
    val offset: String? = null,
) {
    val isWholeDay: Boolean get() = hour == null || minute == null

    /** 0 for Monday: Sakamoto's method, since the phone has no calendar library to ask. */
    val weekday: Int
        get() {
            val y = if (month < MARCH) year - 1 else year
            val sunday0 = (y + y / 4 - y / 100 + y / 400 + MONTH_OFFSETS[month - 1] + day) % DAYS_IN_WEEK
            return (sunday0 + DAYS_IN_WEEK - 1) % DAYS_IN_WEEK
        }

    private companion object {
        const val MARCH = 3
        const val DAYS_IN_WEEK = 7
        val MONTH_OFFSETS = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    }
}
