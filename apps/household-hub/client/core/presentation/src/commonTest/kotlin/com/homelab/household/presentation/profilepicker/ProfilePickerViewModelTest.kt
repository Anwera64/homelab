package com.homelab.household.presentation.profilepicker

import app.cash.turbine.test
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.usecase.ListMembersUseCase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
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

/** "Who's here?" lists the household's faces and hands the one tapped on to the PIN pad. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePickerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val listMembers = mock<ListMembersUseCase>()

    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")
    private val liam = Member(id = "liam", name = "Liam", avatarColor = "#C05638")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN a household of two WHEN the picker opens THEN it is loading and then shows both members`() =
        runTest(testDispatcher) {
            everySuspend { listMembers() } returns listOf(emma, liam)
            val viewModel = ProfilePickerViewModel(listMembers)

            assertEquals(PickerStatus.Loading, viewModel.uiState.value.status)
            advanceUntilIdle()

            assertEquals(PickerStatus.Loaded(listOf(emma, liam)), viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN an unreachable hub WHEN the picker opens THEN the status says unreachable`() =
        runTest(testDispatcher) {
            everySuspend { listMembers() } throws ServerOfflineException()
            val viewModel = ProfilePickerViewModel(listMembers)
            advanceUntilIdle()

            assertEquals(PickerStatus.Unreachable, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN the hub fails in an unexpected way WHEN the picker opens THEN the status says failed`() =
        runTest(testDispatcher) {
            everySuspend { listMembers() } throws IllegalStateException("odd")
            val viewModel = ProfilePickerViewModel(listMembers)
            advanceUntilIdle()

            assertEquals(PickerStatus.Failed, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN an unreachable hub that has since come back WHEN trying again THEN the picker is loading and then shows the member`() =
        runTest(testDispatcher) {
            everySuspend { listMembers() } throws ServerOfflineException()
            val viewModel = ProfilePickerViewModel(listMembers)
            advanceUntilIdle()
            everySuspend { listMembers() } returns listOf(emma)

            viewModel.load()
            assertEquals(PickerStatus.Loading, viewModel.uiState.value.status)
            advanceUntilIdle()

            assertEquals(PickerStatus.Loaded(listOf(emma)), viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN a loaded picker WHEN tapping a face THEN the screen goes to that members pin`() =
        runTest(testDispatcher) {
            everySuspend { listMembers() } returns listOf(emma, liam)
            val viewModel = ProfilePickerViewModel(listMembers)
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onSelectMember(liam)
                advanceUntilIdle()

                assertEquals(ProfilePickerEvent.GoToPin(liam), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
