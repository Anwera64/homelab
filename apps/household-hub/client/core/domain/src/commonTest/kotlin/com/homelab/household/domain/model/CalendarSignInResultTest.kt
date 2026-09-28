package com.homelab.household.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** What the browser hands back when a Google sign-in ends, read into what happened. */
class CalendarSignInResultTest {
    @Test
    fun `GIVEN the hub sent the browser back connected WHEN the address is read THEN the calendar is connected`() {
        // GIVEN
        val address = "hyggehub://calendar/connected"

        // WHEN
        val result = CalendarSignInResult.fromCallback(address)

        // THEN
        assertEquals(CalendarSignInResult.Connected, result)
    }

    @Test
    fun `GIVEN the hub sent each reason back WHEN the address is read THEN the reason is kept`() {
        // GIVEN
        val reasons =
            mapOf(
                "denied" to CalendarSignInFailure.DENIED,
                "expired" to CalendarSignInFailure.EXPIRED,
                "rejected" to CalendarSignInFailure.REJECTED,
                "unreachable" to CalendarSignInFailure.UNREACHABLE,
                "failed" to CalendarSignInFailure.FAILED,
            )

        // WHEN
        val results =
            reasons.keys.associateWith {
                CalendarSignInResult.fromCallback(
                    "hyggehub://calendar/failed?reason=$it",
                )
            }

        // THEN
        reasons.forEach { (wire, failure) -> assertEquals(CalendarSignInResult.Failed(failure), results[wire]) }
    }

    @Test
    fun `GIVEN a reason this app does not know WHEN the address is read THEN it is a plain failure`() {
        // GIVEN
        val address = "hyggehub://calendar/failed?reason=something_new"

        // WHEN
        val result = CalendarSignInResult.fromCallback(address)

        // THEN
        assertEquals(CalendarSignInResult.Failed(CalendarSignInFailure.FAILED), result)
    }

    @Test
    fun `GIVEN the browser was closed before the hub answered WHEN nothing comes back THEN the sign-in was cancelled`() {
        // GIVEN
        val address: String? = null

        // WHEN
        val result = CalendarSignInResult.fromCallback(address)

        // THEN
        assertEquals(CalendarSignInResult.Cancelled, result)
    }

    @Test
    fun `GIVEN an address that is not a calendar sign-in WHEN it is read THEN it is a plain failure`() {
        // GIVEN
        val address = "https://example.com/elsewhere"

        // WHEN
        val result = CalendarSignInResult.fromCallback(address)

        // THEN
        assertEquals(CalendarSignInResult.Failed(CalendarSignInFailure.FAILED), result)
    }
}
