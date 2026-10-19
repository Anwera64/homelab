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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `GIVEN an empty code field WHEN typing a longer lower-case code THEN it is upper-cased and stops at six`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()

            viewModel.onCodeChange("k7m2qp9")

            assertEquals("K7M2QP", viewModel.uiState.value.code)
        }

    @Test
    fun `GIVEN an empty code field WHEN typing characters no code can contain THEN they are ignored`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()

            viewModel.onCodeChange("k-7 m/2")

            assertEquals("K7M2", viewModel.uiState.value.code)
        }

    @Test
    fun `GIVEN a code of only three characters WHEN continuing THEN the status says the code is incomplete`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            viewModel.onCodeChange("k7m")

            viewModel.onContinue()
            advanceUntilIdle()

            assertEquals(InviteCodeStatus.Incomplete, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN a code the hub knows WHEN continuing THEN the join screen opens with the invite and code`() =
        runTest(testDispatcher) {
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
    fun `GIVEN a code the hub does not know WHEN continuing THEN the status says it is invalid`() =
        runTest(testDispatcher) {
            everySuspend { lookUpInvite(any()) } throws InviteInvalidException()
            val viewModel = viewModel()
            viewModel.onCodeChange("zzzzzz")

            viewModel.onContinue()
            advanceUntilIdle()

            assertEquals(InviteCodeStatus.Invalid, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN too much guessing WHEN continuing THEN the lock counts down`() =
        runTest(testDispatcher) {
            everySuspend { lookUpInvite(any()) } throws CodeGuessesLockedException(retryAfterSeconds = 30)
            val viewModel = viewModel()
            viewModel.onCodeChange("zzzzzz")
            viewModel.onContinue()
            advanceTimeBy(1)
            assertEquals(InviteCodeStatus.Locked(secondsLeft = 30), viewModel.uiState.value.status)
            advanceTimeBy(10_000)
            assertEquals(InviteCodeStatus.Locked(secondsLeft = 20), viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN a locked code field WHEN typing a new code THEN the keyboard is ignored`() =
        runTest(testDispatcher) {
            everySuspend { lookUpInvite(any()) } throws CodeGuessesLockedException(retryAfterSeconds = 30)
            val viewModel = viewModel()
            viewModel.onCodeChange("zzzzzz")
            viewModel.onContinue()
            advanceTimeBy(1)
            viewModel.onCodeChange("k7m2qp")
            assertEquals("ZZZZZZ", viewModel.uiState.value.code)
        }

    @Test
    fun `GIVEN an unreachable hub WHEN continuing THEN the status says unreachable`() =
        runTest(testDispatcher) {
            everySuspend { lookUpInvite(any()) } throws ServerOfflineException()
            val viewModel = viewModel()
            viewModel.onCodeChange("k7m2qp")

            viewModel.onContinue()
            advanceUntilIdle()

            assertEquals(InviteCodeStatus.Unreachable, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN the hub fails in an unexpected way WHEN continuing THEN the status is a plain failure`() =
        runTest(testDispatcher) {
            everySuspend { lookUpInvite(any()) } throws IllegalStateException("boom")
            val viewModel = viewModel()
            viewModel.onCodeChange("k7m2qp")

            viewModel.onContinue()
            advanceUntilIdle()

            assertEquals(InviteCodeStatus.Failed, viewModel.uiState.value.status)
        }
}
