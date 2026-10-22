package com.homelab.household.presentation.calendarchoose

import com.homelab.household.domain.model.CalendarSource

/** Whether the account's calendars are on screen yet. */
enum class CalendarChooseStatus {
    /** Asking the account which calendars it has. */
    Loading,

    Ready,

    /** The account, or the hub, didn't answer. Nothing changed: the calendars read stay as they were. */
    LoadFailed,
}

/**
 * The account's [calendars] as the member is setting them, before Done saves them. [saving] while the
 * hub keeps the pick; [saveFailed] when it couldn't, with every switch left as the member set it.
 */
data class CalendarChooseUiState(
    val status: CalendarChooseStatus = CalendarChooseStatus.Loading,
    val calendars: List<CalendarSource> = emptyList(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    /** The calendars agents may add to and change. */
    val editable: List<CalendarSource> get() = calendars.filter { it.writable }

    /** Holidays, subscriptions and anything shared read only: agents may only read these. */
    val readOnly: List<CalendarSource> get() = calendars.filterNot { it.writable }

    val pickedCount: Int get() = calendars.count { it.picked }
}

/** What happens once the member is done with the list. */
sealed interface CalendarChooseEvent {
    data object Done : CalendarChooseEvent
}
