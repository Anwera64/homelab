package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.CalendarSource
import com.homelab.household.domain.repository.CalendarRepository
import com.homelab.household.domain.usecase.PickCalendarsUseCase

class PickCalendarsUseCaseImpl(
    private val calendarRepository: CalendarRepository,
) : PickCalendarsUseCase {
    override suspend operator fun invoke(keys: List<String>): List<CalendarSource> =
        calendarRepository.pickCalendars(keys)
}
