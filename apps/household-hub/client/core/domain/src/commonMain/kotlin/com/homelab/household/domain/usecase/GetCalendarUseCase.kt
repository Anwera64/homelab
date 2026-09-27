package com.homelab.household.domain.usecase

import com.homelab.household.domain.model.CalendarConnection

/** The member's connected calendar, or null when there is none. */
fun interface GetCalendarUseCase {
    suspend operator fun invoke(): CalendarConnection?
}
