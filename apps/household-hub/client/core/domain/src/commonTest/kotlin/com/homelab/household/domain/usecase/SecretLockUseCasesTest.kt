package com.homelab.household.domain.usecase

import com.homelab.household.domain.assertThrowsSuspend
import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.impl.LockSecretSessionsUseCaseImpl
import com.homelab.household.domain.usecase.impl.UnlockSecretSessionUseCaseImpl
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecretLockUseCasesTest {

    private val sessionRepo = mockk<SessionRepository>()
    private val lockSecretSessionsUseCase = LockSecretSessionsUseCaseImpl(sessionRepo)
    private val unlockSecretSessionUseCase = UnlockSecretSessionUseCaseImpl(sessionRepo)

    @Test
    fun lock_secret_sessions_marks_sessions_locked() = runTest {
        coEvery { sessionRepo.lockAllSecretSessions() } returns 3

        val lockedCount = lockSecretSessionsUseCase()

        assertEquals(3, lockedCount)
    }

    @Test
    fun unlock_secret_session_with_valid_pin_succeeds() = runTest {
        coEvery { sessionRepo.unlockSecretSession("session-1", "1234") } returns true

        val success = unlockSecretSessionUseCase("session-1", "1234")

        assertTrue(success)
    }

    @Test
    fun unlock_secret_session_with_invalid_pin_throws_unauthorized() = runTest {
        coEvery {
            sessionRepo.unlockSecretSession("session-1", "wrong")
        } throws UnauthorizedException("Incorrect PIN or password")

        assertThrowsSuspend<UnauthorizedException> {
            unlockSecretSessionUseCase("session-1", "wrong")
        }
    }
}
