package com.homelab.household.presentation.viewmodel

import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.CheckAuthStatusUseCase
import com.homelab.household.domain.usecase.LoginUseCase
import com.homelab.household.domain.usecase.FirstRunOnboardUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val loginUseCase = mockk<LoginUseCase>()
    private val onboardUseCase = mockk<FirstRunOnboardUseCase>()
    private val checkAuthStatusUseCase = mockk<CheckAuthStatusUseCase>()

    private lateinit var viewModel: AuthViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AuthViewModel(
            loginUseCase = loginUseCase,
            onboardUseCase = onboardUseCase,
            checkAuthStatusUseCase = checkAuthStatusUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun check_status_updates_initialization_state() = runTest(testDispatcher) {
        coEvery { checkAuthStatusUseCase() } returns AuthStatus(isInitialized = true, memberCount = 3)

        viewModel.checkStatus()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isInitialized)
        assertEquals(3, state.memberCount)
    }

    @Test
    fun login_success_updates_user_and_auth_state() = runTest(testDispatcher) {
        val user = User(
            id = "u-1",
            username = "alice",
            email = "alice@homelab.local",
            fullName = "Alice Doe",
            isAdmin = true,
            isActive = true
        )
        coEvery { loginUseCase("alice", "secret123") } returns user

        viewModel.login("alice", "secret123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isAuthenticated)
        assertEquals("alice", state.user?.username)
        assertNull(state.errorMessage)
    }

    @Test
    fun login_failure_sets_error_message() = runTest(testDispatcher) {
        coEvery { loginUseCase("alice", "wrong") } throws UnauthorizedException("Invalid credentials")

        viewModel.login("alice", "wrong")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAuthenticated)
        assertNull(state.user)
        assertEquals("Invalid credentials", state.errorMessage)
    }
}
