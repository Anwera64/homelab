package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.CalendarSource

/** Every calendar of the member's connected account, each marked picked or not. */
fun interface GetCalendarSourcesUseCase {
    suspend operator fun invoke(): List<CalendarSource>
}
