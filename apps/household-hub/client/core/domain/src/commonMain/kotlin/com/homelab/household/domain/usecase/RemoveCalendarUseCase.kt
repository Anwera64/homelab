package com.homelab.household.domain.usecase

/** Forgets the member's calendar connection on the hub. */
fun interface RemoveCalendarUseCase {
    suspend operator fun invoke()
}
