package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.CalendarSource

/** Sets which calendars agents read, by their [CalendarSource.key], and returns the list as the hub keeps it. */
fun interface PickCalendarsUseCase {
    suspend operator fun invoke(keys: List<String>): List<CalendarSource>
}
