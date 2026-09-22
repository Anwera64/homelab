package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.User
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.impl.CheckAuthStatusUseCaseImpl
import com.homelab.household.domain.usecase.impl.FirstRunOnboardUseCaseImpl
import com.homelab.household.domain.usecase.impl.HasStoredSessionUseCaseImpl
import com.homelab.household.domain.usecase.impl.ListMembersUseCaseImpl
import com.homelab.household.domain.usecase.impl.LoginUseCaseImpl
import com.homelab.household.domain.usecase.impl.LogoutUseCaseImpl
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthUseCasesTest {
    private val authRepo = mock<AuthRepository>()
    private val loginUseCase = LoginUseCaseImpl(authRepo)
    private val onboardUseCase = FirstRunOnboardUseCaseImpl(authRepo)
    private val listMembersUseCase = ListMembersUseCaseImpl(authRepo)
    private val checkAuthStatusUseCase = CheckAuthStatusUseCaseImpl(authRepo)
    private val logoutUseCase = LogoutUseCaseImpl(authRepo)
    private val hasStoredSessionUseCase = HasStoredSessionUseCaseImpl(authRepo)

    private val emma =
        User(
            id = "emma",
            fullName = "Emma",
            isAdmin = true,
            isActive = true,
            personalSpaceId = "space-1",
            avatarColor = "#3C6E4E",
        )

    @Test
    fun signing_in_passes_the_member_and_pin_to_the_hub() =
        runTest {
            everySuspend { authRepo.login("emma", "482913") } returns emma

            val result = loginUseCase("emma", "482913")

            assertEquals(emma, result)
        }

    @Test
    fun a_wrong_pin_reaches_the_caller_as_it_came_from_the_hub() =
        runTest {
            everySuspend { authRepo.login("emma", "000000") } throws WrongPinException(attemptsLeft = 3)

            assertFailsWith<WrongPinException> { loginUseCase("emma", "000000") }
        }

    @Test
    fun a_pin_that_is_not_six_digits_never_reaches_the_hub() =
        runTest {
            listOf("", "12345", "1234567", "12a456").forEach { pin ->
                assertFailsWith<ValidationException> { loginUseCase("emma", pin) }
            }
            verifySuspend(VerifyMode.exactly(0)) { authRepo.login(any(), any()) }
        }

    @Test
    fun first_run_sends_the_trimmed_name_the_pin_and_the_colour() =
        runTest {
            everySuspend { authRepo.onboard("Emma", "482913", "#C05638") } returns emma

            val result = onboardUseCase("  Emma ", "482913", "#C05638")

            assertEquals(emma, result)
        }

    @Test
    fun first_run_needs_a_name_and_a_six_digit_pin() =
        runTest {
            assertFailsWith<ValidationException> { onboardUseCase("   ", "482913", "#3C6E4E") }
            assertFailsWith<ValidationException> { onboardUseCase("x".repeat(129), "482913", "#3C6E4E") }
            assertFailsWith<ValidationException> { onboardUseCase("Emma", "4829", "#3C6E4E") }

            verifySuspend(VerifyMode.exactly(0)) { authRepo.onboard(any(), any(), any()) }
        }

    @Test
    fun listing_members_asks_the_hub() =
        runTest {
            val members = listOf(Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E"))
            everySuspend { authRepo.listMembers() } returns members

            assertEquals(members, listMembersUseCase())
        }

    @Test
    fun check_auth_status_returns_initialization_state() =
        runTest {
            val status = AuthStatus(isInitialized = true, memberCount = 2)
            everySuspend { authRepo.checkStatus() } returns status

            val result = checkAuthStatusUseCase()

            assertEquals(true, result.isInitialized)
            assertEquals(2, result.memberCount)
        }

    /** Asked before anything is drawn, so it can't wait on the hub. */
    @Test
    fun a_stored_session_is_answered_without_the_hub() {
        every { authRepo.hasStoredSession() } returns true

        assertEquals(true, hasStoredSessionUseCase())

        every { authRepo.hasStoredSession() } returns false

        assertEquals(false, hasStoredSessionUseCase())
    }

    @Test
    fun logout_invokes_repo_logout() =
        runTest {
            everySuspend { authRepo.logout() } returns Unit

            logoutUseCase()

            verifySuspend(VerifyMode.exactly(1)) { authRepo.logout() }
        }
}
