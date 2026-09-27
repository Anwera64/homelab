package com.homelab.household.data.repository

import com.homelab.household.data.datasource.remote.`interface`.CalendarRemoteDataSource
import com.homelab.household.data.dto.CalendarCredentialCreateDto
import com.homelab.household.data.dto.CalendarCredentialReadDto
import com.homelab.household.domain.exception.CalendarUnreachableException
import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.model.CalendarProvider
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
            connectedAt = "2026-09-26T20:04:00",
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
                    calendarName = null,
                )
            everySuspend { remote.configureCalendar(expected) } returns
                googleDto.copy(provider = "apple_icloud", url = "https://caldav.icloud.com")

            val saved =
                repository(remote).connectCalendar(
                    provider = CalendarProvider.APPLE,
                    server = "https://caldav.icloud.com",
                    account = "emma@icloud.com",
                    password = "abcd-efgh-ijkl-mnop",
                    calendarName = null,
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
                    calendarName = null,
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
}
