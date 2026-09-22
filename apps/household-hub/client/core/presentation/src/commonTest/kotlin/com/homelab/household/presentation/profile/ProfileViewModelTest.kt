package com.homelab.household.presentation.profile

import app.cash.turbine.test
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.ListHouseholdMembersUseCase
import com.homelab.household.domain.usecase.LogoutUseCase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
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

/** Your own account, and the one thing it cannot do: leave while you are the only admin. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val getCurrentUser = mock<GetCurrentUserUseCase>()
    private val listHouseholdMembers = mock<ListHouseholdMembersUseCase>()
    private val logout = mock<LogoutUseCase>()

    private val emma = User(id = "emma", fullName = "Emma", isAdmin = true, isActive = true)
    private val liam = User(id = "liam", fullName = "Liam", isAdmin = false, isActive = true)
    private val noor = User(id = "noor", fullName = "Noor", isAdmin = true, isActive = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ProfileViewModel(getCurrentUser, listHouseholdMembers, logout)

    @Test
    fun the_only_admin_is_told_they_cannot_leave() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(emma, viewModel.uiState.value.member)
            assertEquals(true, viewModel.uiState.value.isSoleAdmin)
            assertEquals(ProfileStatus.Ready, viewModel.uiState.value.status)
        }

    @Test
    fun an_admin_with_another_admin_beside_them_can_leave() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, noor)

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isSoleAdmin)
        }

    @Test
    fun a_member_who_is_not_an_admin_can_always_leave() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns liam
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isSoleAdmin)
        }

    /**
     * Offline, the member comes off the phone and the household does not. Losing the member with the
     * household is what drew an empty circle where a name and a colour belong.
     */
    @Test
    fun the_member_is_shown_even_when_the_household_cannot_be_listed() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } throws ServerOfflineException()

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(emma, viewModel.uiState.value.member)
            assertEquals(ProfileStatus.Unreachable, viewModel.uiState.value.status)
        }

    /** Nobody can be promoted offline either, so the question is left alone rather than guessed. */
    @Test
    fun a_household_that_cannot_be_listed_leaves_the_sole_admin_question_unanswered() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } throws ServerOfflineException()

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isSoleAdmin)
        }

    @Test
    fun an_unreachable_hub_says_so_and_can_be_asked_again() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } throws ServerOfflineException()

            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(ProfileStatus.Unreachable, viewModel.uiState.value.status)

            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)
            viewModel.load()
            advanceUntilIdle()

            assertEquals(ProfileStatus.Ready, viewModel.uiState.value.status)
        }

    @Test
    fun signing_out_forgets_the_token_and_says_so() =
        runTest(testDispatcher) {
            everySuspend { getCurrentUser() } returns emma
            everySuspend { listHouseholdMembers() } returns listOf(emma, liam)
            everySuspend { logout() } returns Unit
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onSignOut()
                advanceUntilIdle()

                assertEquals(ProfileEvent.SignedOut, awaitItem())
            }
            verifySuspend(VerifyMode.exactly(1)) { logout() }
        }
}
