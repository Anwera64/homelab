package com.homelab.household.domain.usecase

import com.homelab.household.domain.assertThrowsSuspend
import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuthUseCasesTest {

    private val authRepo = mockk<AuthRepository>()
    private val loginUseCase = LoginUseCase(authRepo)
    private val onboardUseCase = FirstRunOnboardUseCase(authRepo)
    private val listMembersUseCase = ListMembersUseCase(authRepo)
    private val checkAuthStatusUseCase = CheckAuthStatusUseCase(authRepo)
    private val logoutUseCase = LogoutUseCase(authRepo)

    private val emma = User(
        id = "emma",
        fullName = "Emma",
        isAdmin = true,
        isActive = true,
        personalSpaceId = "space-1",
        avatarColor = "#3C6E4E"
    )

    @Test
    fun signing_in_passes_the_member_and_pin_to_the_hub() = runTest {
        coEvery { authRepo.login("emma", "482913") } returns emma

        val result = loginUseCase("emma", "482913")

        assertEquals(emma, result)
    }

    @Test
    fun a_wrong_pin_reaches_the_caller_as_it_came_from_the_hub() = runTest {
        coEvery { authRepo.login("emma", "000000") } throws WrongPinException(attemptsLeft = 3)

        assertThrowsSuspend<WrongPinException> { loginUseCase("emma", "000000") }
    }

    @Test
    fun a_pin_that_is_not_six_digits_never_reaches_the_hub() = runTest {
        listOf("", "12345", "1234567", "12a456").forEach { pin ->
            assertThrowsSuspend<ValidationException> { loginUseCase("emma", pin) }
        }
        coVerify(exactly = 0) { authRepo.login(any(), any()) }
    }

    @Test
    fun first_run_sends_the_trimmed_name_the_pin_and_the_colour() = runTest {
        coEvery { authRepo.onboard("Emma", "482913", "#C05638") } returns emma

        val result = onboardUseCase("  Emma ", "482913", "#C05638")

        assertEquals(emma, result)
    }

    @Test
    fun first_run_needs_a_name_and_a_six_digit_pin() = runTest {
        assertThrowsSuspend<ValidationException> { onboardUseCase("   ", "482913", "#3C6E4E") }
        assertThrowsSuspend<ValidationException> { onboardUseCase("x".repeat(129), "482913", "#3C6E4E") }
        assertThrowsSuspend<ValidationException> { onboardUseCase("Emma", "4829", "#3C6E4E") }

        coVerify(exactly = 0) { authRepo.onboard(any(), any(), any()) }
    }

    @Test
    fun listing_members_asks_the_hub() = runTest {
        val members = listOf(Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E"))
        coEvery { authRepo.listMembers() } returns members

        assertEquals(members, listMembersUseCase())
    }

    @Test
    fun check_auth_status_returns_initialization_state() = runTest {
        val status = AuthStatus(isInitialized = true, memberCount = 2)
        coEvery { authRepo.checkStatus() } returns status

        val result = checkAuthStatusUseCase()

        assertEquals(true, result.isInitialized)
        assertEquals(2, result.memberCount)
    }

    @Test
    fun logout_invokes_repo_logout() = runTest {
        coEvery { authRepo.logout() } returns Unit

        logoutUseCase()

        coVerify(exactly = 1) { authRepo.logout() }
    }
}
