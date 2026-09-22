package com.homelab.household.presentation.pinforgot

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

/** Recovery is social: the screen names who can vouch for you, and never yourself. */
@OptIn(ExperimentalCoroutinesApi::class)
class PinForgotViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val listMembers = mock<ListMembersUseCase>()

    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")
    private val liam = Member(id = "liam", name = "Liam", avatarColor = "#C05638")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_others_are_who_you_can_ask() =
        runTest(testDispatcher) {
            everySuspend { listMembers() } returns listOf(emma, liam)

            val viewModel = PinForgotViewModel(emma, listMembers)
            advanceUntilIdle()

            assertEquals(listOf(liam), viewModel.uiState.value.others)
            assertEquals(PinForgotStatus.Ready, viewModel.uiState.value.status)
        }

    @Test
    fun living_alone_leaves_only_the_hub_itself() =
        runTest(testDispatcher) {
            everySuspend { listMembers() } returns listOf(emma)

            val viewModel = PinForgotViewModel(emma, listMembers)
            advanceUntilIdle()

            assertEquals(emptyList(), viewModel.uiState.value.others)
        }

    @Test
    fun an_unreachable_hub_says_so_and_can_be_asked_again() =
        runTest(testDispatcher) {
            everySuspend { listMembers() } throws ServerOfflineException()

            val viewModel = PinForgotViewModel(emma, listMembers)
            advanceUntilIdle()
            assertEquals(PinForgotStatus.Unreachable, viewModel.uiState.value.status)

            everySuspend { listMembers() } returns listOf(emma, liam)
            viewModel.load()
            advanceUntilIdle()

            assertEquals(PinForgotStatus.Ready, viewModel.uiState.value.status)
        }
}
