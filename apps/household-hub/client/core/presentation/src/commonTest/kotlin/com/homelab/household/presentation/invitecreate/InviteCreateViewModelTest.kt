package com.homelab.household.presentation.invitecreate

import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.Invite
import com.homelab.household.domain.usecase.CreateInviteUseCase
import com.homelab.household.presentation.firstrun.NameError
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Making the code an admin reads out, and watching it run down. */
@OptIn(ExperimentalCoroutinesApi::class)
class InviteCreateViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val createInvite = mock<CreateInviteUseCase>()
    private val invite = Invite(code = "K7M2QP", invitedName = "Liam", isAdmin = false, expiresInSeconds = 900)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = InviteCreateViewModel(createInvite)

    @Test
    fun `GIVEN a typed name WHEN creating an invite THEN a code shows and counts down`() =
        runTest(testDispatcher) {
            everySuspend { createInvite(any(), any()) } returns invite
            val viewModel = viewModel()
            viewModel.onNameChange("Liam")

            viewModel.create()
            advanceTimeBy(1)

            assertEquals(invite, viewModel.uiState.value.invite)
            assertEquals(900, viewModel.uiState.value.secondsLeft)

            advanceTimeBy(10_000)
            assertEquals(890, viewModel.uiState.value.secondsLeft)
        }

    @Test
    fun `GIVEN a blank name WHEN creating an invite THEN the name field says it is missing`() =
        runTest(testDispatcher) {
            val viewModel = viewModel()

            viewModel.create()
            advanceUntilIdle()

            assertEquals(NameError.Missing, viewModel.uiState.value.nameError)
        }

    @Test
    fun `GIVEN a name the household already has WHEN creating an invite THEN the name field says it is taken`() =
        runTest(testDispatcher) {
            everySuspend { createInvite(any(), any()) } throws NameTakenException()
            val viewModel = viewModel()
            viewModel.onNameChange("Emma")

            viewModel.create()
            advanceUntilIdle()

            assertEquals(NameError.Taken, viewModel.uiState.value.nameError)
        }

    @Test
    fun `GIVEN a typed name and the admin switch on WHEN creating an invite THEN the hub is asked for an admin invite`() =
        runTest(testDispatcher) {
            everySuspend { createInvite(any(), any()) } returns invite.copy(isAdmin = true)
            val viewModel = viewModel()
            viewModel.onNameChange("Noor")
            viewModel.onAdminChange(true)

            viewModel.create()
            advanceTimeBy(1)

            verifySuspend(VerifyMode.exactly(1)) { createInvite("Noor", true) }
        }

    @Test
    fun `GIVEN a code nobody used WHEN its time runs out THEN the status is expired`() =
        runTest(testDispatcher) {
            everySuspend { createInvite(any(), any()) } returns invite.copy(expiresInSeconds = 2)
            val viewModel = viewModel()
            viewModel.onNameChange("Liam")
            viewModel.create()
            advanceTimeBy(3_000)
            assertEquals(InviteCreateStatus.Expired, viewModel.uiState.value.status)
        }

    @Test
    fun `GIVEN an expired code WHEN asking for a new one THEN a fresh code replaces it`() =
        runTest(testDispatcher) {
            everySuspend { createInvite(any(), any()) } returns invite.copy(expiresInSeconds = 2)
            val viewModel = viewModel()
            viewModel.onNameChange("Liam")
            viewModel.create()
            advanceTimeBy(3_000)
            viewModel.newCode()
            advanceTimeBy(1)

            assertEquals(InviteCreateStatus.Idle, viewModel.uiState.value.status)
            assertEquals(
                invite.code,
                viewModel.uiState.value.invite
                    ?.code,
            )
        }

    @Test
    fun `GIVEN an unreachable hub WHEN creating an invite THEN the status says unreachable`() =
        runTest(testDispatcher) {
            everySuspend { createInvite(any(), any()) } throws ServerOfflineException()
            val viewModel = viewModel()
            viewModel.onNameChange("Liam")

            viewModel.create()
            advanceUntilIdle()

            assertEquals(InviteCreateStatus.Unreachable, viewModel.uiState.value.status)
        }
}
