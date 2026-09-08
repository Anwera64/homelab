package com.homelab.household.domain.usecase

import com.homelab.household.domain.assertThrowsSuspend
import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.model.AuthStatus
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
    private val checkAuthStatusUseCase = CheckAuthStatusUseCase(authRepo)
    private val logoutUseCase = LogoutUseCase(authRepo)

    private val dummyUser = User(
        id = "user-1",
        username = "alice",
        email = "alice@homelab.local",
        fullName = "Alice Doe",
        isAdmin = true,
        isActive = true,
        personalSpaceId = "space-1",
        avatarColor = "#4F46E5"
    )

    @Test
    fun login_with_valid_credentials_returns_user() = runTest {
        coEvery { authRepo.login("alice", "password123") } returns dummyUser

        val result = loginUseCase("alice", "password123")

        assertEquals("user-1", result.id)
        assertEquals("alice", result.username)
        coVerify(exactly = 1) { authRepo.login("alice", "password123") }
    }

    @Test
    fun login_with_invalid_credentials_throws_unauthorized() = runTest {
        coEvery { authRepo.login("alice", "wrongpass") } throws UnauthorizedException("Invalid username or password")

        assertThrowsSuspend<UnauthorizedException> {
            loginUseCase("alice", "wrongpass")
        }
    }

    @Test
    fun login_with_short_username_or_password_throws_validation_error() = runTest {
        assertThrowsSuspend<ValidationException> {
            loginUseCase("al", "password123")
        }
        assertThrowsSuspend<ValidationException> {
            loginUseCase("alice", "short")
        }
    }

    @Test
    fun onboard_with_valid_params_delegates_to_repo() = runTest {
        coEvery {
            authRepo.onboard("admin", "admin@homelab.local", "secretpass123", "Admin User", "#4F46E5")
        } returns dummyUser

        val result = onboardUseCase("admin", "admin@homelab.local", "secretpass123", "Admin User", "#4F46E5")

        assertEquals(dummyUser.id, result.id)
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
