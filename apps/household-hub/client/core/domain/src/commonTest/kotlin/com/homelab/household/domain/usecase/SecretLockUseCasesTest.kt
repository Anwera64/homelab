package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.impl.LockSecretSessionsUseCaseImpl
import com.homelab.household.domain.usecase.impl.UnlockSecretSessionUseCaseImpl
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SecretLockUseCasesTest {

    private val sessionRepo = mock<SessionRepository>()
    private val lockSecretSessionsUseCase = LockSecretSessionsUseCaseImpl(sessionRepo)
    private val unlockSecretSessionUseCase = UnlockSecretSessionUseCaseImpl(sessionRepo)

    @Test
    fun lock_secret_sessions_marks_sessions_locked() = runTest {
        everySuspend { sessionRepo.lockAllSecretSessions() } returns 3

        val lockedCount = lockSecretSessionsUseCase()

        assertEquals(3, lockedCount)
    }

    @Test
    fun unlock_secret_session_with_valid_pin_succeeds() = runTest {
        everySuspend { sessionRepo.unlockSecretSession("session-1", "1234") } returns true

        val success = unlockSecretSessionUseCase("session-1", "1234")

        assertTrue(success)
    }

    @Test
    fun unlock_secret_session_with_invalid_pin_throws_unauthorized() = runTest {
        everySuspend {
            sessionRepo.unlockSecretSession("session-1", "wrong")
        } throws UnauthorizedException("Incorrect PIN or password")

        assertFailsWith<UnauthorizedException> {
            unlockSecretSessionUseCase("session-1", "wrong")
        }
    }
}

