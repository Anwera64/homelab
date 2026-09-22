package com.homelab.household.presentation.pinentry

import app.cash.turbine.test
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.LoginUseCase
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The PIN pad signs in on the sixth digit. A miss clears the dots and says how many tries are
 * left; a lock counts down and ignores the pad until it's over.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PinEntryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val login = mock<LoginUseCase>()

    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")
    private val signedIn = User(id = "emma", fullName = "Emma", isAdmin = true, isActive = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PinEntryViewModel(member = emma, loginUseCase = login)

    private fun PinEntryViewModel.type(digits: String) = digits.forEach(::onDigit)

    private fun TestScope.state(viewModel: PinEntryViewModel): PinEntryUiState {
        runCurrent()
        return viewModel.uiState.value
    }

    @Test
    fun it_opens_empty_for_the_member_that_was_tapped() {
        val state = viewModel().uiState.value

        assertEquals(emma, state.member)
        assertEquals(0, state.entered)
        assertEquals(PinStatus.Idle, state.status)
    }

    @Test
    fun digits_fill_the_dots_and_delete_takes_the_last_one_back() {
        val viewModel = viewModel()

        viewModel.type("482")
        viewModel.onDelete()

        assertEquals(2, viewModel.uiState.value.entered)
    }

    @Test
    fun the_sixth_digit_signs_in_and_moves_on() =
        runTest(testDispatcher) {
            everySuspend { login("emma", "482913") } returns signedIn
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.type("482913")
                advanceUntilIdle()

                assertEquals(PinEntryEvent.SignedIn, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verifySuspend(VerifyMode.exactly(1)) { login("emma", "482913") }
        }

    @Test
    fun digits_typed_while_the_hub_is_checking_are_ignored() =
        runTest(testDispatcher) {
            everySuspend { login(any(), any()) } calls {
                delay(1_000)
                signedIn
            }
            val viewModel = viewModel()

            viewModel.type("482913")
            runCurrent()
            viewModel.type("7")

            assertEquals(PinStatus.Checking, viewModel.uiState.value.status)
            assertEquals(6, viewModel.uiState.value.entered)
        }

    @Test
    fun a_wrong_pin_clears_the_dots_and_says_how_many_tries_are_left() =
        runTest(testDispatcher) {
            everySuspend { login(any(), any()) } throws WrongPinException(attemptsLeft = 2)
            val viewModel = viewModel()

            viewModel.type("000000")
            advanceUntilIdle()

            assertEquals(PinStatus.WrongPin(attemptsLeft = 2), viewModel.uiState.value.status)
            assertEquals(0, viewModel.uiState.value.entered)
        }

    @Test
    fun a_lock_counts_down_each_second_and_ignores_the_pad_meanwhile() =
        runTest(testDispatcher) {
            everySuspend { login(any(), any()) } throws PinLockedException(retryAfterSeconds = 30)
            val viewModel = viewModel()

            viewModel.type("000000")
            assertEquals(PinStatus.Locked(secondsLeft = 30), state(viewModel).status)

            viewModel.type("1")
            assertEquals(0, viewModel.uiState.value.entered)

            advanceTimeBy(1_000)
            assertEquals(PinStatus.Locked(secondsLeft = 29), state(viewModel).status)

            advanceTimeBy(29_000)
            assertEquals(PinStatus.Idle, state(viewModel).status)

            viewModel.type("1")
            assertEquals(1, viewModel.uiState.value.entered)
        }

    @Test
    fun an_unreachable_hub_is_reported_and_the_dots_cleared() =
        runTest(testDispatcher) {
            everySuspend { login(any(), any()) } throws ServerOfflineException()
            val viewModel = viewModel()

            viewModel.type("482913")
            advanceUntilIdle()

            assertEquals(PinStatus.Unreachable, viewModel.uiState.value.status)
            assertEquals(0, viewModel.uiState.value.entered)
        }

    @Test
    fun anything_else_is_reported_as_failed() =
        runTest(testDispatcher) {
            everySuspend { login(any(), any()) } throws IllegalStateException("odd")
            val viewModel = viewModel()

            viewModel.type("482913")
            advanceUntilIdle()

            assertEquals(PinStatus.Failed, viewModel.uiState.value.status)
        }
}
