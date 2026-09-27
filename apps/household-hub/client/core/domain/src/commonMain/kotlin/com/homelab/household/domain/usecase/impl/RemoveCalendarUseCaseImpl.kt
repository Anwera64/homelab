package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.CalendarRepository
import com.homelab.household.domain.usecase.RemoveCalendarUseCase

class RemoveCalendarUseCaseImpl(
    private val calendarRepository: CalendarRepository,
) : RemoveCalendarUseCase {
    override suspend operator fun invoke() = calendarRepository.removeCalendar()
}
