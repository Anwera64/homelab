package com.homelab.household.domain.usecase

/** A member leaves the household for good, confirming with their own PIN. */
fun interface LeaveHouseholdUseCase {
    suspend operator fun invoke(pin: String)
}
