package com.homelab.household.presentation.join

import app.cash.turbine.test
import com.homelab.household.domain.exception.InviteInvalidException
import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.JoinHouseholdUseCase
import com.homelab.household.domain.usecase.ListMembersUseCase
import com.homelab.household.presentation.firstrun.AvatarPalette
import com.homelab.household.presentation.firstrun.NameError
import com.homelab.household.presentation.firstrun.PinError
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Joining with a code: the name comes from the invite and can be corrected, the PIN is the
 * joiner's own, and a colour somebody here already wears is not on offer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JoinViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val joinHousehold = mock<JoinHouseholdUseCase>()
    private val listMembers = mock<ListMembersUseCase>()

    private val invite = InvitePreview(invitedName = "Liam", inviterName = "Emma", inviterAvatarColor = "#3C6E4E")
    private val liam = User(id = "liam", fullName = "Liam", isAdmin = false, isActive = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        everySuspend { listMembers() } returns emptyList()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = JoinViewModel(invite, "K7M2QP", joinHousehold, listMembers)

    @Test
    fun the_form_starts_with_the_name_from_the_invite() = runTest(testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("Liam", viewModel.uiState.value.name)
        assertEquals(AvatarPalette.swatches.first(), viewModel.uiState.value.colour)
    }

    @Test
    fun a_colour_somebody_here_wears_is_marked_and_not_chosen() = runTest(testDispatcher) {
        val taken = AvatarPalette.swatches.first()
        everySuspend { listMembers() } returns listOf(Member(id = "emma", name = "Emma", avatarColor = taken))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(setOf(taken), viewModel.uiState.value.takenColours)
        assertEquals(AvatarPalette.swatches[1], viewModel.uiState.value.colour)

        viewModel.onColourSelect(taken)
        assertEquals(AvatarPalette.swatches[1], viewModel.uiState.value.colour)
    }

    @Test
    fun joining_sends_the_name_pin_and_colour_and_says_so() = runTest(testDispatcher) {
        everySuspend { joinHousehold(any(), any(), any(), any()) } returns liam
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPinChange("975310")

        viewModel.events.test {
            viewModel.join()
            advanceUntilIdle()

            assertEquals(JoinEvent.Joined, awaitItem())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            joinHousehold("K7M2QP", "Liam", "975310", AvatarPalette.swatches.first())
        }
    }

    @Test
    fun a_name_that_was_wiped_out_says_so_under_the_field() = runTest(testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onNameChange("   ")
        viewModel.onPinChange("975310")

        viewModel.join()
        advanceUntilIdle()

        assertEquals(NameError.Missing, viewModel.uiState.value.nameError)
    }

    @Test
    fun a_pin_that_is_not_six_digits_says_so_under_the_field() = runTest(testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPinChange("975")

        viewModel.join()
        advanceUntilIdle()

        assertEquals(PinError.NotSixDigits, viewModel.uiState.value.pinError)
    }

    @Test
    fun a_name_taken_while_they_typed_lands_under_the_name() = runTest(testDispatcher) {
        everySuspend { joinHousehold(any(), any(), any(), any()) } throws NameTakenException()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPinChange("975310")

        viewModel.join()
        advanceUntilIdle()

        assertEquals(NameError.Taken, viewModel.uiState.value.nameError)
        assertEquals(JoinStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun a_code_that_has_gone_says_so() = runTest(testDispatcher) {
        everySuspend { joinHousehold(any(), any(), any(), any()) } throws InviteInvalidException()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPinChange("975310")

        viewModel.join()
        advanceUntilIdle()

        assertEquals(JoinStatus.Expired, viewModel.uiState.value.status)
    }

    @Test
    fun an_unreachable_hub_says_so() = runTest(testDispatcher) {
        everySuspend { joinHousehold(any(), any(), any(), any()) } throws ServerOfflineException()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPinChange("975310")

        viewModel.join()
        advanceUntilIdle()

        assertEquals(JoinStatus.Unreachable, viewModel.uiState.value.status)
    }
}
