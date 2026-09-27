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
    fun details_the_hub_can_use_are_connected_and_the_screen_says_so() =
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
    fun a_refused_password_says_rejected_and_keeps_what_was_typed() =
        runTest(testDispatcher) {
            everySuspend { connectCalendar(any(), any(), any(), any(), any()) } throws CalendarRejectedException()
            val viewModel = filledIn()

            viewModel.connect()
            advanceUntilIdle()

            assertEquals(CalendarConnectStatus.Rejected, viewModel.uiState.value.status)
            assertEquals("abcd-efgh-ijkl-mnop", viewModel.uiState.value.password)
        }

    @Test
    fun a_server_the_hub_cannot_reach_says_so_rather_than_blaming_the_password() =
        runTest(testDispatcher) {
            everySuspend { connectCalendar(any(), any(), any(), any(), any()) } throws CalendarUnreachableException()
            val viewModel = filledIn()

            viewModel.connect()
            advanceUntilIdle()

            assertEquals(CalendarConnectStatus.CalendarUnreachable, viewModel.uiState.value.status)
        }

    @Test
    fun a_hub_that_does_not_answer_is_its_own_failure() =
        runTest(testDispatcher) {
            everySuspend { connectCalendar(any(), any(), any(), any(), any()) } throws ServerOfflineException()
            val viewModel = filledIn()

            viewModel.connect()
            advanceUntilIdle()

            assertEquals(CalendarConnectStatus.HubUnreachable, viewModel.uiState.value.status)
        }

    @Test
    fun tapping_connect_early_says_what_is_missing_and_asks_nothing() =
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
