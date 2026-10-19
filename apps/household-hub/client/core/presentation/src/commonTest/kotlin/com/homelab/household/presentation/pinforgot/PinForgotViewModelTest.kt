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
    fun `GIVEN a household of two WHEN the screen loads THEN the other member is who you can ask and it is ready`() =
        runTest(testDispatcher) {
            everySuspend { listMembers() } returns listOf(emma, liam)

            val viewModel = PinForgotViewModel(emma, listMembers)
            advanceUntilIdle()

            assertEquals(listOf(liam), viewModel.uiState.value.others)
            assertEquals(PinForgotStatus.Ready, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN a household of one WHEN the screen loads THEN nobody else is listed`() =
        runTest(testDispatcher) {
            everySuspend { listMembers() } returns listOf(emma)

            val viewModel = PinForgotViewModel(emma, listMembers)
            advanceUntilIdle()

            assertEquals(emptyList(), viewModel.uiState.value.others)
        }

    @Test
    fun `GIVEN an unreachable hub WHEN the screen loads THEN the status says unreachable`() =
        runTest(testDispatcher) {
            everySuspend { listMembers() } throws ServerOfflineException()
            val viewModel = PinForgotViewModel(emma, listMembers)
            advanceUntilIdle()
            assertEquals(PinForgotStatus.Unreachable, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN an unreachable hub that has since come back WHEN loading again THEN the status is ready`() =
        runTest(testDispatcher) {
            everySuspend { listMembers() } throws ServerOfflineException()
            val viewModel = PinForgotViewModel(emma, listMembers)
            advanceUntilIdle()
            everySuspend { listMembers() } returns listOf(emma, liam)
            viewModel.load()
            advanceUntilIdle()

            assertEquals(PinForgotStatus.Ready, viewModel.uiState.value.status)
        }
}
