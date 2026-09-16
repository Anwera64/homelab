package com.homelab.household.presentation.invitecode

import app.cash.turbine.test
import com.homelab.household.domain.exception.CodeGuessesLockedException
import com.homelab.household.domain.exception.InviteInvalidException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.domain.usecase.LookUpInviteUseCase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Typing the code that gets someone into the household. The button is never disabled, so a short
 * code is answered with words, and a hub that has had enough guessing counts down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InviteCodeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val lookUpInvite = mock<LookUpInviteUseCase>()
    private val invite = InvitePreview(invitedName = "Liam", inviterName = "Emma", inviterAvatarColor = "#3C6E4E")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = InviteCodeViewModel(lookUpInvite)

    @Test
    fun what_is_typed_is_upper_cased_and_stops_at_six() = runTest(testDispatcher) {
        val viewModel = viewModel()

        viewModel.onCodeChange("k7m2qp9")

        assertEquals("K7M2QP", viewModel.uiState.value.code)
    }

    @Test
    fun characters_no_code_can_contain_are_ignored() = runTest(testDispatcher) {
        val viewModel = viewModel()

        viewModel.onCodeChange("k-7 m/2")

        assertEquals("K7M2", viewModel.uiState.value.code)
    }

    @Test
    fun a_short_code_is_answered_with_words_rather_than_a_dead_button() = runTest(testDispatcher) {
        val viewModel = viewModel()
        viewModel.onCodeChange("k7m")

        viewModel.onContinue()
        advanceUntilIdle()

        assertEquals(InviteCodeStatus.Incomplete, viewModel.uiState.value.status)
    }

    @Test
    fun a_code_the_hub_knows_opens_the_join_screen() = runTest(testDispatcher) {
        everySuspend { lookUpInvite(any()) } returns invite
        val viewModel = viewModel()
        viewModel.onCodeChange("k7m2qp")

        viewModel.events.test {
            viewModel.onContinue()
            advanceUntilIdle()

            assertEquals(InviteCodeEvent.GoToJoin(invite, "K7M2QP"), awaitItem())
        }
    }

    @Test
    fun a_code_the_hub_does_not_know_says_so() = runTest(testDispatcher) {
        everySuspend { lookUpInvite(any()) } throws InviteInvalidException()
        val viewModel = viewModel()
        viewModel.onCodeChange("zzzzzz")

        viewModel.onContinue()
        advanceUntilIdle()

        assertEquals(InviteCodeStatus.Invalid, viewModel.uiState.value.status)
    }

    @Test
    fun too_much_guessing_counts_down_and_ignores_the_keyboard() = runTest(testDispatcher) {
        everySuspend { lookUpInvite(any()) } throws CodeGuessesLockedException(retryAfterSeconds = 30)
        val viewModel = viewModel()
        viewModel.onCodeChange("zzzzzz")

        viewModel.onContinue()
        advanceTimeBy(1)

        assertEquals(InviteCodeStatus.Locked(secondsLeft = 30), viewModel.uiState.value.status)

        viewModel.onCodeChange("k7m2qp")
        assertEquals("ZZZZZZ", viewModel.uiState.value.code)

        advanceTimeBy(10_000)
        assertEquals(InviteCodeStatus.Locked(secondsLeft = 20), viewModel.uiState.value.status)
    }

    @Test
    fun an_unreachable_hub_says_so() = runTest(testDispatcher) {
        everySuspend { lookUpInvite(any()) } throws ServerOfflineException()
        val viewModel = viewModel()
        viewModel.onCodeChange("k7m2qp")

        viewModel.onContinue()
        advanceUntilIdle()

        assertEquals(InviteCodeStatus.Unreachable, viewModel.uiState.value.status)
    }

    @Test
    fun anything_else_the_hub_does_is_a_plain_failure() = runTest(testDispatcher) {
        everySuspend { lookUpInvite(any()) } throws IllegalStateException("boom")
        val viewModel = viewModel()
        viewModel.onCodeChange("k7m2qp")

        viewModel.onContinue()
        advanceUntilIdle()

        assertEquals(InviteCodeStatus.Failed, viewModel.uiState.value.status)
    }
}
