package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.CalendarSource
import com.homelab.household.domain.repository.CalendarRepository
import com.homelab.household.domain.usecase.GetCalendarSourcesUseCase

class GetCalendarSourcesUseCaseImpl(
    private val calendarRepository: CalendarRepository,
) : GetCalendarSourcesUseCase {
    override suspend operator fun invoke(): List<CalendarSource> = calendarRepository.listCalendars()
}
