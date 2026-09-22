package com.homelab.household.presentation.firstrun

import app.cash.turbine.test
import com.homelab.household.domain.exception.HubAlreadySetUpException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.FirstRunOnboardUseCase
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * First run takes a name, a PIN and a colour. The button is never disabled: tapping it with
 * something missing says what, under the field that's missing it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirstRunViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val onboard = mock<FirstRunOnboardUseCase>()

    private val emma = User(id = "emma", fullName = "Emma", isAdmin = true, isActive = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun filledIn() =
        FirstRunViewModel(onboard).apply {
            onNameChange("Emma")
            onPinChange("482913")
        }

    @Test
    fun it_starts_with_the_first_swatch_chosen() {
        val state = FirstRunViewModel(onboard).uiState.value

        assertEquals(AvatarPalette.swatches.first(), state.colour)
        assertEquals("", state.name)
        assertEquals("", state.pin)
    }

    @Test
    fun the_pin_field_keeps_only_the_first_six_digits() {
        val viewModel = FirstRunViewModel(onboard)

        viewModel.onPinChange("12a3456789")

        assertEquals("123456", viewModel.uiState.value.pin)
    }

    @Test
    fun creating_with_nothing_filled_in_says_so_under_each_field_and_sends_nothing() =
        runTest(testDispatcher) {
            val viewModel = FirstRunViewModel(onboard)

            viewModel.create()
            advanceUntilIdle()

            assertEquals(NameError.Missing, viewModel.uiState.value.nameError)
            assertEquals(PinError.NotSixDigits, viewModel.uiState.value.pinError)
            verifySuspend(VerifyMode.exactly(0)) { onboard(any(), any(), any()) }
        }

    @Test
    fun a_name_over_128_characters_is_too_long() =
        runTest(testDispatcher) {
            val viewModel = filledIn().apply { onNameChange("x".repeat(129)) }

            viewModel.create()

            assertEquals(NameError.TooLong, viewModel.uiState.value.nameError)
        }

    @Test
    fun typing_in_a_field_clears_its_error() {
        val viewModel = FirstRunViewModel(onboard)
        viewModel.create()

        viewModel.onNameChange("E")
        viewModel.onPinChange("4")

        assertNull(viewModel.uiState.value.nameError)
        assertNull(viewModel.uiState.value.pinError)
    }

    @Test
    fun creating_sends_the_name_pin_and_colour_and_goes_home() =
        runTest(testDispatcher) {
            everySuspend { onboard("Emma", "482913", "#C05638") } returns emma
            val viewModel = filledIn().apply { onColourSelect("#C05638") }

            viewModel.events.test {
                viewModel.create()
                advanceUntilIdle()

                assertEquals(FirstRunEvent.GoToHome, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun an_unreachable_hub_is_reported_and_nothing_typed_is_cleared() =
        runTest(testDispatcher) {
            everySuspend { onboard(any(), any(), any()) } throws ServerOfflineException()
            val viewModel = filledIn()

            viewModel.create()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(FirstRunFailure.Unreachable, state.failure)
            assertEquals("Emma", state.name)
            assertEquals("482913", state.pin)
            assertFalse(state.isCreating)
        }

    @Test
    fun a_hub_someone_else_already_set_up_says_so() =
        runTest(testDispatcher) {
            everySuspend { onboard(any(), any(), any()) } throws HubAlreadySetUpException()
            val viewModel = filledIn()

            viewModel.create()
            advanceUntilIdle()

            assertEquals(FirstRunFailure.AlreadySetUp, viewModel.uiState.value.failure)
        }

    @Test
    fun anything_else_is_an_unknown_failure() =
        runTest(testDispatcher) {
            everySuspend { onboard(any(), any(), any()) } throws IllegalStateException("odd")
            val viewModel = filledIn()

            viewModel.create()
            advanceUntilIdle()

            assertEquals(FirstRunFailure.Unknown, viewModel.uiState.value.failure)
        }
}
