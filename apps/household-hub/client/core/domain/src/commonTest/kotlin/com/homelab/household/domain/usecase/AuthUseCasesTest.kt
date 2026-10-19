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
    fun `GIVEN a member and a PIN WHEN signing in THEN the hub is asked with them and the member comes back`() =
        runTest {
            everySuspend { authRepo.login("emma", "482913") } returns emma

            val result = loginUseCase("emma", "482913")

            assertEquals(emma, result)
        }

    @Test
    fun `GIVEN the hub rejecting the PIN WHEN signing in THEN the wrong PIN reaches the caller`() =
        runTest {
            everySuspend { authRepo.login("emma", "000000") } throws WrongPinException(attemptsLeft = 3)

            assertFailsWith<WrongPinException> { loginUseCase("emma", "000000") }
        }

    @Test
    fun `GIVEN PINs that are not six digits WHEN signing in THEN each is refused and the hub is never asked`() =
        runTest {
            listOf("", "12345", "1234567", "12a456").forEach { pin ->
                assertFailsWith<ValidationException> { loginUseCase("emma", pin) }
            }
            verifySuspend(VerifyMode.exactly(0)) { authRepo.login(any(), any()) }
        }

    @Test
    fun `GIVEN a name with stray spaces WHEN the first member is set up THEN the hub gets the trimmed name with the PIN and colour`() =
        runTest {
            everySuspend { authRepo.onboard("Emma", "482913", "#C05638") } returns emma

            val result = onboardUseCase("  Emma ", "482913", "#C05638")

            assertEquals(emma, result)
        }

    @Test
    fun `GIVEN a blank name or a too long name or a short PIN WHEN the first member is set up THEN each is refused and the hub is never asked`() =
        runTest {
            assertFailsWith<ValidationException> { onboardUseCase("   ", "482913", "#3C6E4E") }
            assertFailsWith<ValidationException> { onboardUseCase("x".repeat(129), "482913", "#3C6E4E") }
            assertFailsWith<ValidationException> { onboardUseCase("Emma", "4829", "#3C6E4E") }

            verifySuspend(VerifyMode.exactly(0)) { authRepo.onboard(any(), any(), any()) }
        }

    @Test
    fun `GIVEN a hub with one member WHEN members are listed THEN that member comes back`() =
        runTest {
            val members = listOf(Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E"))
            everySuspend { authRepo.listMembers() } returns members

            assertEquals(members, listMembersUseCase())
        }

    @Test
    fun `GIVEN an initialized hub with two members WHEN the auth status is checked THEN it reports initialized with two members`() =
        runTest {
            val status = AuthStatus(isInitialized = true, memberCount = 2)
            everySuspend { authRepo.checkStatus() } returns status

            val result = checkAuthStatusUseCase()

            assertEquals(true, result.isInitialized)
            assertEquals(2, result.memberCount)
        }

    /** Asked before anything is drawn, so it can't wait on the hub. */
    @Test
    fun `GIVEN a token kept on this phone WHEN a stored session is asked about THEN it says yes without the hub`() {
        every { authRepo.hasStoredSession() } returns true

        assertEquals(true, hasStoredSessionUseCase())
    }

    @Test
    fun `GIVEN no token kept on this phone WHEN a stored session is asked about THEN it says no without the hub`() {
        every { authRepo.hasStoredSession() } returns false

        assertEquals(false, hasStoredSessionUseCase())
    }

    @Test
    fun `GIVEN a signed-in member WHEN logging out THEN the repository logs out once`() =
        runTest {
            everySuspend { authRepo.logout() } returns Unit

            logoutUseCase()

            verifySuspend(VerifyMode.exactly(1)) { authRepo.logout() }
        }
}
