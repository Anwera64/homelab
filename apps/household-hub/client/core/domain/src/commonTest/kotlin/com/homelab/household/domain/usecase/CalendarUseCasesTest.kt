package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.CalendarRejectedException
import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.domain.repository.CalendarRepository
import com.homelab.household.domain.usecase.impl.ConnectCalendarUseCaseImpl
import com.homelab.household.domain.usecase.impl.GetCalendarUseCaseImpl
import com.homelab.household.domain.usecase.impl.RemoveCalendarUseCaseImpl
import com.homelab.household.domain.usecase.impl.StartGoogleCalendarSignInUseCaseImpl
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Connecting the member's calendar: which server each provider uses, and what reaches the hub. */
class CalendarUseCasesTest {
    private val repository = mock<CalendarRepository>()
    private val connect = ConnectCalendarUseCaseImpl(repository)

    private val icloud =
        CalendarConnection(
            provider = CalendarProvider.APPLE,
            account = "emma@icloud.com",
            server = "https://caldav.icloud.com",
            calendarName = "Default",
        )

    @Test
    fun `GIVEN an Apple account WHEN it is connected THEN iCloud's own server is used`() =
        runTest {
            everySuspend {
                repository.connectCalendar(
                    CalendarProvider.APPLE,
                    "https://caldav.icloud.com",
                    "emma@icloud.com",
                    "abcd-efgh-ijkl-mnop",
                    null,
                )
            } returns icloud

            val connection =
                connect(CalendarProvider.APPLE, " emma@icloud.com ", "abcd-efgh-ijkl-mnop", server = "ignored")

            assertEquals(icloud, connection)
        }

    @Test
    fun `GIVEN a Google account WHEN it is connected with a password THEN nothing is sent as Google only takes a sign-in`() =
        runTest {
            assertFailsWith<ValidationException> {
                connect(CalendarProvider.GOOGLE, "emma.larsson@gmail.com", "abcd efgh ijkl mnop")
            }

            verifySuspend(VerifyMode.not) { repository.connectCalendar(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `GIVEN the hub can start a Google sign-in WHEN a member starts one THEN they get Google's page to open`() =
        runTest {
            everySuspend { repository.startGoogleSignIn() } returns
                "https://accounts.google.com/o/oauth2/v2/auth?state=s"

            val page = StartGoogleCalendarSignInUseCaseImpl(repository)()

            assertEquals("https://accounts.google.com/o/oauth2/v2/auth?state=s", page)
        }

    @Test
    fun `GIVEN another CalDAV server WHEN it is connected THEN the typed address and calendar are sent`() =
        runTest {
            val other = icloud.copy(provider = CalendarProvider.OTHER)
            everySuspend {
                repository.connectCalendar(
                    CalendarProvider.OTHER,
                    "https://cloud.example.com/remote.php/dav",
                    "emma",
                    "secret",
                    "Family",
                )
            } returns other

            val connection =
                connect(
                    CalendarProvider.OTHER,
                    "emma",
                    "secret",
                    server = " https://cloud.example.com/remote.php/dav ",
                    calendarName = " Family ",
                )

            assertEquals(other, connection)
        }

    @Test
    fun `GIVEN another CalDAV server with no address WHEN it is connected THEN nothing is sent`() =
        runTest {
            assertFailsWith<ValidationException> { connect(CalendarProvider.OTHER, "emma", "secret", server = " ") }
        }

    @Test
    fun `GIVEN no password WHEN a calendar is connected THEN nothing is sent`() =
        runTest {
            assertFailsWith<ValidationException> { connect(CalendarProvider.APPLE, "emma@icloud.com", "  ") }
        }

    @Test
    fun `GIVEN the calendar refuses the password WHEN it is connected THEN the refusal reaches the caller`() =
        runTest {
            everySuspend {
                repository.connectCalendar(
                    CalendarProvider.APPLE,
                    "https://caldav.icloud.com",
                    "emma@icloud.com",
                    "my-apple-id-password",
                    null,
                )
            } throws CalendarRejectedException()

            assertFailsWith<CalendarRejectedException> {
                connect(CalendarProvider.APPLE, "emma@icloud.com", "my-apple-id-password")
            }
        }

    @Test
    fun `GIVEN no calendar WHEN the member's calendar is asked for THEN there is none`() =
        runTest {
            everySuspend { repository.getCalendar() } returns null

            assertNull(GetCalendarUseCaseImpl(repository)())
        }

    @Test
    fun `GIVEN a connected calendar WHEN it is removed THEN the repository is asked to forget it`() =
        runTest {
            everySuspend { repository.removeCalendar() } returns Unit

            RemoveCalendarUseCaseImpl(repository)()

            verifySuspend(VerifyMode.exactly(1)) { repository.removeCalendar() }
        }
}
