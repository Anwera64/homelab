package com.homelab.household.presentation.calendarchoose

import app.cash.turbine.test
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.CalendarSource
import com.homelab.household.domain.usecase.GetCalendarSourcesUseCase
import com.homelab.household.domain.usecase.PickCalendarsUseCase
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
import kotlin.test.assertTrue

/** Which of the account's calendars agents read (#252): shown after connecting and from the profile. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarChooseViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val getCalendars = mock<GetCalendarSourcesUseCase>()
    private val pickCalendars = mock<PickCalendarsUseCase>()

    private val home = CalendarSource(key = "c1", name = "Home", writable = true, picked = true)
    private val work = CalendarSource(key = "c2", name = "Work", writable = true, picked = true)
    private val holidays = CalendarSource(key = "c3", name = "Holidays in Spain", writable = false, picked = false)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun opened(): CalendarChooseViewModel = CalendarChooseViewModel(getCalendars, pickCalendars)

    @Test
    fun `GIVEN an account with three calendars WHEN the list opens THEN it shows them as the hub keeps them`() =
        runTest(testDispatcher) {
            everySuspend { getCalendars() } returns listOf(home, work, holidays)
            val viewModel = opened()

            advanceUntilIdle()

            assertEquals(CalendarChooseStatus.Ready, viewModel.uiState.value.status)
            assertEquals(listOf(home, work, holidays), viewModel.uiState.value.calendars)
        }

    @Test
    fun `GIVEN the hub can't be reached WHEN the list opens THEN it says the account didn't answer`() =
        runTest(testDispatcher) {
            everySuspend { getCalendars() } throws ServerOfflineException()
            val viewModel = opened()

            advanceUntilIdle()

            assertEquals(CalendarChooseStatus.LoadFailed, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN the list failed to open WHEN the member tries again THEN the calendars show`() =
        runTest(testDispatcher) {
            everySuspend { getCalendars() } throws ServerOfflineException()
            val viewModel = opened()
            advanceUntilIdle()
            everySuspend { getCalendars() } returns listOf(home)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(listOf(home), viewModel.uiState.value.calendars)
        }

    @Test
    fun `GIVEN work is read WHEN the member turns it off THEN it shows off before anything is saved`() =
        runTest(testDispatcher) {
            everySuspend { getCalendars() } returns listOf(home, work, holidays)
            val viewModel = opened()
            advanceUntilIdle()

            viewModel.onToggle("c2")

            assertEquals(false, viewModel.uiState.value.calendars.first { it.key == "c2" }.picked)
            verifySuspend(VerifyMode.not) { pickCalendars(any()) }
        }

    @Test
    fun `GIVEN work turned off WHEN the member is done THEN the hub reads home only and the list closes`() =
        runTest(testDispatcher) {
            everySuspend { getCalendars() } returns listOf(home, work, holidays)
            everySuspend { pickCalendars(listOf("c1")) } returns listOf(home, work.copy(picked = false), holidays)
            val viewModel = opened()
            advanceUntilIdle()
            viewModel.onToggle("c2")

            viewModel.events.test {
                viewModel.onDone()
                assertEquals(CalendarChooseEvent.Done, awaitItem())
            }
            verifySuspend { pickCalendars(listOf("c1")) }
        }

    @Test
    fun `GIVEN nothing was changed WHEN the member is done THEN nothing is sent and the list closes`() =
        runTest(testDispatcher) {
            everySuspend { getCalendars() } returns listOf(home, work, holidays)
            val viewModel = opened()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onDone()
                assertEquals(CalendarChooseEvent.Done, awaitItem())
            }
            verifySuspend(VerifyMode.not) { pickCalendars(any()) }
        }

    @Test
    fun `GIVEN the hub can't save WHEN the member is done THEN the switches stay as set and it says so`() =
        runTest(testDispatcher) {
            everySuspend { getCalendars() } returns listOf(home, work, holidays)
            everySuspend { pickCalendars(any()) } throws ServerOfflineException()
            val viewModel = opened()
            advanceUntilIdle()
            viewModel.onToggle("c3")

            viewModel.onDone()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.saveFailed)
            assertEquals(true, viewModel.uiState.value.calendars.first { it.key == "c3" }.picked)
        }
}
