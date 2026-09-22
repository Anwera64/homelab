package com.homelab.household.presentation.removemember

import app.cash.turbine.test
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.usecase.RemoveMemberUseCase
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

/** Removing someone means typing their name: friction proportional to the damage. */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoveMemberViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val removeMember = mock<RemoveMemberUseCase>()
    private val liam = Member(id = "liam", name = "Liam", avatarColor = "#C05638")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = RemoveMemberViewModel(liam, removeMember)

    @Test
    fun their_name_typed_out_removes_them() =
        runTest(testDispatcher) {
            everySuspend { removeMember(any()) } returns Unit
            val viewModel = viewModel()
            viewModel.onNameChange("  liam ")

            viewModel.events.test {
                viewModel.remove()
                advanceUntilIdle()

                assertEquals(RemoveMemberEvent.Removed, awaitItem())
            }
            verifySuspend(VerifyMode.exactly(1)) { removeMember("liam") }
        }

    @Test
    fun another_name_is_answered_with_words_rather_than_a_dead_button() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            viewModel.onNameChange("Li")

            viewModel.remove()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.nameMismatch)
        }

    @Test
    fun an_unreachable_hub_says_nobody_was_removed() =
        runTest(testDispatcher) {
            everySuspend { removeMember(any()) } throws ServerOfflineException()
            val viewModel = viewModel()
            viewModel.onNameChange("Liam")

            viewModel.remove()
            advanceUntilIdle()

            assertEquals(RemoveMemberStatus.Unreachable, viewModel.uiState.value.status)
        }
}
