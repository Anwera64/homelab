package com.homelab.household.domain.usecase.impl

import com.homelab.household.domain.repository.CalendarRepository
import com.homelab.household.domain.usecase.StartGoogleCalendarSignInUseCase

class StartGoogleCalendarSignInUseCaseImpl(
    private val calendarRepository: CalendarRepository,
) : StartGoogleCalendarSignInUseCase {
    override suspend operator fun invoke(): String = calendarRepository.startGoogleSignIn()
}
