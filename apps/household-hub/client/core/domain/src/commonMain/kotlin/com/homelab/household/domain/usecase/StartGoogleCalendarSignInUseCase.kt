package com.homelab.household.domain.usecase

/** Asks the hub for Google's sign-in page, for the member to open in a browser. */
interface StartGoogleCalendarSignInUseCase {
    suspend operator fun invoke(): String
}
