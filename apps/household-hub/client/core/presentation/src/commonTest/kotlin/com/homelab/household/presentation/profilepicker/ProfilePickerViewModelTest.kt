package com.homelab.household.presentation.profilepicker

import app.cash.turbine.test
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.usecase.ListMembersUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** "Who's here?" lists the household's faces and hands the one tapped on to the PIN pad. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePickerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val listMembers = mockk<ListMembersUseCase>()

    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")
    private val liam = Member(id = "liam", name = "Liam", avatarColor = "#C05638")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun it_loads_the_members_as_soon_as_it_opens() = runTest(testDispatcher) {
        coEvery { listMembers() } returns listOf(emma, liam)
        val viewModel = ProfilePickerViewModel(listMembers)

        assertEquals(PickerStatus.Loading, viewModel.uiState.value.status)
        advanceUntilIdle()

        assertEquals(PickerStatus.Loaded(listOf(emma, liam)), viewModel.uiState.value.status)
    }

    @Test
    fun an_unreachable_hub_is_reported() = runTest(testDispatcher) {
        coEvery { listMembers() } throws ServerOfflineException()
        val viewModel = ProfilePickerViewModel(listMembers)
        advanceUntilIdle()

        assertEquals(PickerStatus.Unreachable, viewModel.uiState.value.status)
    }

    @Test
    fun any_other_failure_is_reported_as_failed() = runTest(testDispatcher) {
        coEvery { listMembers() } throws IllegalStateException("odd")
        val viewModel = ProfilePickerViewModel(listMembers)
        advanceUntilIdle()

        assertEquals(PickerStatus.Failed, viewModel.uiState.value.status)
    }

    @Test
    fun trying_again_reloads() = runTest(testDispatcher) {
        coEvery { listMembers() } throws ServerOfflineException()
        val viewModel = ProfilePickerViewModel(listMembers)
        advanceUntilIdle()
        coEvery { listMembers() } returns listOf(emma)

        viewModel.load()
        assertEquals(PickerStatus.Loading, viewModel.uiState.value.status)
        advanceUntilIdle()

        assertEquals(PickerStatus.Loaded(listOf(emma)), viewModel.uiState.value.status)
    }

    @Test
    fun tapping_a_face_goes_to_that_members_pin() = runTest(testDispatcher) {
        coEvery { listMembers() } returns listOf(emma, liam)
        val viewModel = ProfilePickerViewModel(listMembers)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onMemberSelected(liam)
            advanceUntilIdle()

            assertEquals(ProfilePickerEvent.GoToPin(liam), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
