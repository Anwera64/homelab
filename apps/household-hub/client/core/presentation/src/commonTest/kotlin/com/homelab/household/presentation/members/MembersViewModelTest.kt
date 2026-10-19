package com.homelab.household.presentation.members

import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.ListHouseholdMembersUseCase
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

/** The members list: you first, and what the screen may offer depends on whether you are the admin. */
@OptIn(ExperimentalCoroutinesApi::class)
class MembersViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val listHouseholdMembers = mock<ListHouseholdMembersUseCase>()
    private val getCurrentUser = mock<GetCurrentUserUseCase>()

    private val emma = User(id = "emma", fullName = "Emma", isAdmin = true, isActive = true, avatarColor = "#3C6E4E")
    private val liam = User(id = "liam", fullName = "Liam", isAdmin = false, isActive = true, avatarColor = "#C05638")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MembersViewModel(listHouseholdMembers, getCurrentUser)

    @Test
    fun `GIVEN a household where you are not the admin WHEN the list loads THEN you come first and are marked as yourself`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns liam
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)

            val viewModel = viewModel()
            advanceUntilIdle()

            val rows = viewModel.uiState.value.rows
            assertEquals(listOf("Liam", "Emma"), rows.map { it.name })
            assertEquals(true, rows.first().isYou)
            assertEquals(true, rows.last().isAdmin)
            assertEquals(false, viewModel.uiState.value.youAreAdmin)
        }

    @Test
    fun `GIVEN you are the admin WHEN the list loads THEN the screen knows you are an admin and is ready`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.youAreAdmin)
            assertEquals(MembersStatus.Ready, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN an unreachable hub WHEN the list loads THEN the status says unreachable`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } throws ServerOfflineException()
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(MembersStatus.Unreachable, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN an unreachable hub that has since come back WHEN loading again THEN the status is ready`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } throws ServerOfflineException()
            val viewModel = viewModel()
            advanceUntilIdle()
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)
            viewModel.load()
            advanceUntilIdle()

            assertEquals(MembersStatus.Ready, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN the hub fails in an unexpected way WHEN the list loads THEN the status is a plain failure`() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } throws IllegalStateException("boom")

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(MembersStatus.Failed, viewModel.uiState.value.status)
        }
}
