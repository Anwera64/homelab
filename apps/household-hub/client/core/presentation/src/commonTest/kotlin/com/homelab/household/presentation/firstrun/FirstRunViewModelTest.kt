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
    fun `GIVEN a new first run screen WHEN it opens THEN the first swatch is chosen and the name and pin are empty`() {
        val state = FirstRunViewModel(onboard).uiState.value

        assertEquals(AvatarPalette.swatches.first(), state.colour)
        assertEquals("", state.name)
        assertEquals("", state.pin)
    }

    @Test
    fun `GIVEN a first run screen WHEN typing a pin with a letter and too many digits THEN only the first six digits are kept`() {
        val viewModel = FirstRunViewModel(onboard)

        viewModel.onPinChange("12a3456789")

        assertEquals("123456", viewModel.uiState.value.pin)
    }

    @Test
    fun `GIVEN nothing filled in WHEN creating THEN each field says so and nothing is sent to the hub`() =
        runTest(testDispatcher) {
            val viewModel = FirstRunViewModel(onboard)

            viewModel.create()
            advanceUntilIdle()

            assertEquals(NameError.Missing, viewModel.uiState.value.nameError)
            assertEquals(PinError.NotSixDigits, viewModel.uiState.value.pinError)
            verifySuspend(VerifyMode.exactly(0)) { onboard(any(), any(), any()) }
        }

    @Test
    fun `GIVEN a name over 128 characters WHEN creating THEN the name field says it is too long`() =
        runTest(testDispatcher) {
            val viewModel = filledIn().apply { onNameChange("x".repeat(129)) }

            viewModel.create()

            assertEquals(NameError.TooLong, viewModel.uiState.value.nameError)
        }

    @Test
    fun `GIVEN a name error WHEN typing in the name field THEN the name error clears`() {
        val viewModel = FirstRunViewModel(onboard)
        viewModel.create()
        viewModel.onNameChange("E")
        assertNull(viewModel.uiState.value.nameError)
    }

    @Test
    fun `GIVEN a pin error WHEN typing in the pin field THEN the pin error clears`() {
        val viewModel = FirstRunViewModel(onboard)
        viewModel.create()
        viewModel.onPinChange("4")
        assertNull(viewModel.uiState.value.pinError)
    }

    @Test
    fun `GIVEN a filled-in form and a chosen colour WHEN creating THEN the hub gets the name and pin and colour and the screen goes home`() =
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
    fun `GIVEN an unreachable hub WHEN creating THEN the failure is unreachable and nothing typed is cleared`() =
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
    fun `GIVEN a hub someone else already set up WHEN creating THEN the failure says it is already set up`() =
        runTest(testDispatcher) {
            everySuspend { onboard(any(), any(), any()) } throws HubAlreadySetUpException()
            val viewModel = filledIn()

            viewModel.create()
            advanceUntilIdle()

            assertEquals(FirstRunFailure.AlreadySetUp, viewModel.uiState.value.failure)
        }

    @Test
    fun `GIVEN the hub fails in an unexpected way WHEN creating THEN the failure is unknown`() =
        runTest(testDispatcher) {
            everySuspend { onboard(any(), any(), any()) } throws IllegalStateException("odd")
            val viewModel = filledIn()

            viewModel.create()
            advanceUntilIdle()

            assertEquals(FirstRunFailure.Unknown, viewModel.uiState.value.failure)
        }
}
