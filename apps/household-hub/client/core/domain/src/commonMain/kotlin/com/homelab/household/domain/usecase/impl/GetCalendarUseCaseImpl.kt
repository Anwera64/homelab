package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.repository.CalendarRepository
import com.homelab.household.domain.usecase.GetCalendarUseCase

class GetCalendarUseCaseImpl(
    private val calendarRepository: CalendarRepository,
) : GetCalendarUseCase {
    override suspend operator fun invoke(): CalendarConnection? = calendarRepository.getCalendar()
}
