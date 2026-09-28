package com.homelab.household.data.datasource.remote.`interface`

import com.homelab.household.data.dto.CalendarCredentialCreateDto
import com.homelab.household.data.dto.CalendarCredentialReadDto

/**
 * Everything the hub is asked about the member's calendar connection. Each function is one call,
 * returning the DTO the hub sent; `CalendarRepositoryImpl` maps them.
 */
interface CalendarRemoteDataSource {
    /** Null when the member has no calendar connected (the hub's 404). */
    suspend fun getMyCalendar(): CalendarCredentialReadDto?

    /** Throws `CalendarRejectedException` or `CalendarUnreachableException` when the hub's test fails. */
    suspend fun configureCalendar(credential: CalendarCredentialCreateDto): CalendarCredentialReadDto

    suspend fun deleteCalendar()

    /** Google's sign-in page. Throws `GoogleSignInUnavailableException` when the hub has no Google sign-in set up. */
    suspend fun startGoogleSignIn(): String
}
