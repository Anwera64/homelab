package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.model.CalendarProvider

/**
 * Connects the member's calendar, replacing any they had. [server] is only read for
 * [CalendarProvider.OTHER]; Apple's is fixed and Google's is built from [account].
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
