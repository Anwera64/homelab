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
    fun `GIVEN an invite for Liam WHEN the form opens THEN it has his name and the first colour`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals("Liam", viewModel.uiState.value.name)
            assertEquals(AvatarPalette.swatches.first(), viewModel.uiState.value.colour)
        }

    @Test
    fun `GIVEN a colour somebody here wears WHEN the form opens THEN it is marked taken and not chosen`() =
        runTest(testDispatcher) {
            val taken = AvatarPalette.swatches.first()
            everySuspend { listMembers() } returns listOf(Member(id = "emma", name = "Emma", avatarColor = taken))
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(setOf(taken), viewModel.uiState.value.takenColours)
            assertEquals(AvatarPalette.swatches[1], viewModel.uiState.value.colour)
        }

    @Test
    fun `GIVEN a colour somebody here wears WHEN selecting it THEN the chosen colour stays the same`() =
        runTest(testDispatcher) {
            val taken = AvatarPalette.swatches.first()
            everySuspend { listMembers() } returns listOf(Member(id = "emma", name = "Emma", avatarColor = taken))
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onColourSelect(taken)
            assertEquals(AvatarPalette.swatches[1], viewModel.uiState.value.colour)
        }

    @Test
    fun `GIVEN a filled-in form WHEN joining THEN the hub gets the code and name and pin and colour and the screen says joined`() =
        runTest(testDispatcher) {
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
    fun `GIVEN a name wiped out to blanks WHEN joining THEN the name field says it is missing`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onNameChange("   ")
            viewModel.onPinChange("975310")

            viewModel.join()
            advanceUntilIdle()

            assertEquals(NameError.Missing, viewModel.uiState.value.nameError)
        }

    @Test
    fun `GIVEN a pin that is not six digits WHEN joining THEN the pin field says so`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onPinChange("975")

            viewModel.join()
            advanceUntilIdle()

            assertEquals(PinError.NotSixDigits, viewModel.uiState.value.pinError)
        }

    @Test
    fun `GIVEN a name someone took while they typed WHEN joining THEN the name field says it is taken and the status is idle`() =
        runTest(testDispatcher) {
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
    fun `GIVEN an invite code that has gone WHEN joining THEN the status says expired`() =
        runTest(testDispatcher) {
            everySuspend { joinHousehold(any(), any(), any(), any()) } throws InviteInvalidException()
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onPinChange("975310")

            viewModel.join()
            advanceUntilIdle()

            assertEquals(JoinStatus.Expired, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN an unreachable hub WHEN joining THEN the status says unreachable`() =
        runTest(testDispatcher) {
            everySuspend { joinHousehold(any(), any(), any(), any()) } throws ServerOfflineException()
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onPinChange("975310")

            viewModel.join()
            advanceUntilIdle()

            assertEquals(JoinStatus.Unreachable, viewModel.uiState.value.status)
        }
}
