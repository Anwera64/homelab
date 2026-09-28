package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.model.CalendarProvider

/**
 * Connects the member's calendar with a password, replacing any they had. [server] is only read for
 * [CalendarProvider.OTHER]; Apple's is fixed. Google signs in instead ([StartGoogleCalendarSignInUseCase]).
 */
interface ConnectCalendarUseCase {
    suspend operator fun invoke(
        provider: CalendarProvider,
        account: String,
        password: String,
        server: String = "",
        calendarName: String = "",
    ): CalendarConnection
}
