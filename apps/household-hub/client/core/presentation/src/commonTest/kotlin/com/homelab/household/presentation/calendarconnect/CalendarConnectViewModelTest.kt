package com.homelab.household.presentation.calendarconnect

import app.cash.turbine.test
import com.homelab.household.domain.exception.CalendarRejectedException
import com.homelab.household.domain.exception.CalendarUnreachableException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.CalendarConnection
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.domain.usecase.ConnectCalendarUseCase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Connecting a calendar: nothing is kept until the hub reaches it, and each failure says its own fix. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarConnectViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val connectCalendar = mock<ConnectCalendarUseCase>()

    private val icloud =
        CalendarConnection(
            provider = CalendarProvider.APPLE,
            account = "emma@icloud.com",
            server = "https://caldav.icloud.com",
            calendarName = "Default",
        )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun filledIn(provider: CalendarProvider = CalendarProvider.APPLE) =
        CalendarConnectViewModel(provider, connectCalendar).apply {
            onAccountChange("emma@icloud.com")
            onPasswordChange("abcd-efgh-ijkl-mnop")
        }

    @Test
    fun `GIVEN details the hub can use WHEN connecting THEN the calendar is connected and the screen says so`() =
        runTest(testDispatcher) {
            everySuspend { connectCalendar(any(), any(), any(), any(), any()) } returns icloud
            val viewModel = filledIn()

            viewModel.events.test {
                viewModel.connect()
                advanceUntilIdle()

                assertEquals(CalendarConnectEvent.Connected, awaitItem())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                connectCalendar(CalendarProvider.APPLE, "emma@icloud.com", "abcd-efgh-ijkl-mnop", "", "")
            }
        }

    @Test
    fun `GIVEN the calendar refuses the password WHEN connecting THEN the status says rejected and the typed password is kept`() =
        runTest(testDispatcher) {
            everySuspend { connectCalendar(any(), any(), any(), any(), any()) } throws CalendarRejectedException()
            val viewModel = filledIn()

            viewModel.connect()
            advanceUntilIdle()

            assertEquals(CalendarConnectStatus.Rejected, viewModel.uiState.value.status)
            assertEquals("abcd-efgh-ijkl-mnop", viewModel.uiState.value.password)
        }

    @Test
    fun `GIVEN a calendar server the hub cannot reach WHEN connecting THEN the status says so rather than blaming the password`() =
        runTest(testDispatcher) {
            everySuspend { connectCalendar(any(), any(), any(), any(), any()) } throws CalendarUnreachableException()
            val viewModel = filledIn()

            viewModel.connect()
            advanceUntilIdle()

            assertEquals(CalendarConnectStatus.CalendarUnreachable, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN a hub that does not answer WHEN connecting THEN the status is its own hub unreachable failure`() =
        runTest(testDispatcher) {
            everySuspend { connectCalendar(any(), any(), any(), any(), any()) } throws ServerOfflineException()
            val viewModel = filledIn()

            viewModel.connect()
            advanceUntilIdle()

            assertEquals(CalendarConnectStatus.HubUnreachable, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN nothing filled in WHEN tapping connect THEN each missing field is flagged and the hub is not asked`() =
        runTest(testDispatcher) {
            val viewModel = CalendarConnectViewModel(CalendarProvider.OTHER, connectCalendar)

            viewModel.connect()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(true, state.accountMissing)
            assertEquals(true, state.passwordMissing)
            assertEquals(true, state.serverMissing)
            verifySuspend(VerifyMode.not) { connectCalendar(any(), any(), any(), any(), any()) }
        }
}
