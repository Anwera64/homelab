package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.CalendarRemoteDataSource
import com.homelab.household.data.dto.CalendarCredentialCreateDto
import com.homelab.household.data.dto.CalendarCredentialReadDto
import com.homelab.household.data.dto.CalendarSourceDto
import com.homelab.household.data.dto.CalendarSourcesPickDto
import com.homelab.household.domain.exception.CalendarUnreachableException
import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.domain.model.CalendarSource
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The calendar repository maps the hub's provider names both ways, and its DTOs to `CalendarConnection`. */
class CalendarRepositoryTest {
    private val googleDto =
        CalendarCredentialReadDto(
            id = "cal-1",
            provider = "google_caldav",
            url = "https://apidata.googleusercontent.com/caldav/v2/emma.larsson@gmail.com/events",
            username = "emma.larsson@gmail.com",
            calendarName = "Default",
            updatedAt = "2026-09-26T20:04:00",
        )

    private val google =
        CalendarConnection(
            provider = CalendarProvider.GOOGLE,
            account = "emma.larsson@gmail.com",
            server = "https://apidata.googleusercontent.com/caldav/v2/emma.larsson@gmail.com/events",
            calendarName = "Default",
            connectedAt = "2026-09-26T20:04:00Z",
        )

    private fun repository(remote: CalendarRemoteDataSource) = CalendarRepositoryImpl(remote = remote)

    @Test
    fun `GIVEN a Google calendar on the hub WHEN it is asked for THEN it comes back as a Google connection`() =
        runTest {
            val remote = mock<CalendarRemoteDataSource>()
            everySuspend { remote.getMyCalendar() } returns googleDto

            assertEquals(google, repository(remote).getCalendar())
        }

    @Test
    fun `GIVEN no calendar on the hub WHEN it is asked for THEN there is none`() =
        runTest {
            val remote = mock<CalendarRemoteDataSource>()
            everySuspend { remote.getMyCalendar() } returns null

            assertNull(repository(remote).getCalendar())
        }

    @Test
    fun `GIVEN an Apple account WHEN it is connected THEN the hub's name for Apple is sent`() =
        runTest {
            val remote = mock<CalendarRemoteDataSource>()
            val expected =
                CalendarCredentialCreateDto(
                    provider = "apple_icloud",
                    url = "https://caldav.icloud.com",
                    username = "emma@icloud.com",
                    password = "abcd-efgh-ijkl-mnop",
                )
            everySuspend { remote.configureCalendar(expected) } returns
                googleDto.copy(provider = "apple_icloud", url = "https://caldav.icloud.com")

            val saved =
                repository(remote).connectCalendar(
                    provider = CalendarProvider.APPLE,
                    server = "https://caldav.icloud.com",
                    account = "emma@icloud.com",
                    password = "abcd-efgh-ijkl-mnop",
                )

            assertEquals(CalendarProvider.APPLE, saved.provider)
            verifySuspend(VerifyMode.exactly(1)) { remote.configureCalendar(expected) }
        }

    @Test
    fun `GIVEN a calendar the hub cannot reach WHEN it is connected THEN the failure reaches the caller`() =
        runTest {
            val remote = mock<CalendarRemoteDataSource>()
            everySuspend {
                remote.configureCalendar(
                    CalendarCredentialCreateDto(
                        provider = "caldav",
                        url = "https://cloud.example.com",
                        username = "emma",
                        password = "secret",
                    ),
                )
            } throws CalendarUnreachableException()

            assertFailsWith<CalendarUnreachableException> {
                repository(remote).connectCalendar(
                    provider = CalendarProvider.OTHER,
                    server = "https://cloud.example.com",
                    account = "emma",
                    password = "secret",
                )
            }
        }

    @Test
    fun `GIVEN a connected calendar WHEN it is removed THEN the data source is asked to delete it`() =
        runTest {
            val remote = mock<CalendarRemoteDataSource>()
            everySuspend { remote.deleteCalendar() } returns Unit

            repository(remote).removeCalendar()

            verifySuspend(VerifyMode.exactly(1)) { remote.deleteCalendar() }
        }

    @Test
    fun `GIVEN Google no longer accepts the sign-in WHEN the calendar is asked for THEN it comes back needing a new one`() =
        runTest {
            val remote = mock<CalendarRemoteDataSource>()
            everySuspend { remote.getMyCalendar() } returns googleDto.copy(needsReconnect = true)

            val calendar = repository(remote).getCalendar()

            assertEquals(google.copy(needsReconnect = true), calendar)
        }

    @Test
    fun `GIVEN the hub can start a Google sign-in WHEN one is started THEN Google's page comes back`() =
        runTest {
            val remote = mock<CalendarRemoteDataSource>()
            everySuspend { remote.startGoogleSignIn() } returns "https://accounts.google.com/o/oauth2/v2/auth?state=s"

            val page = repository(remote).startGoogleSignIn()

            assertEquals("https://accounts.google.com/o/oauth2/v2/auth?state=s", page)
        }

    @Test
    fun `GIVEN an account with calendars WHEN they are listed THEN each comes back as a source`() =
        runTest {
            val remote = mock<CalendarRemoteDataSource>()
            everySuspend { remote.getCalendarSources() } returns
                listOf(
                    CalendarSourceDto(key = "c1", name = "Home", writable = true, picked = true),
                    CalendarSourceDto(key = "c2", name = "Birthdays", writable = false, picked = false),
                )

            assertEquals(
                listOf(
                    CalendarSource(key = "c1", name = "Home", writable = true, picked = true),
                    CalendarSource(key = "c2", name = "Birthdays", writable = false, picked = false),
                ),
                repository(remote).listCalendars(),
            )
        }

    @Test
    fun `GIVEN two calendars ticked WHEN they are picked THEN their keys are sent and the saved list comes back`() =
        runTest {
            val remote = mock<CalendarRemoteDataSource>()
            everySuspend { remote.putCalendarSources(CalendarSourcesPickDto(picked = listOf("c1", "c3"))) } returns
                listOf(CalendarSourceDto(key = "c1", name = "Home", writable = true, picked = true))

            val saved = repository(remote).pickCalendars(listOf("c1", "c3"))

            assertEquals(listOf(CalendarSource(key = "c1", name = "Home", writable = true, picked = true)), saved)
        }
}
