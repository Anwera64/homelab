package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ValidationException
import com.homelab.household.domain.repository.SessionRepository
import com.homelab.household.domain.usecase.impl.LockSecretSessionsUseCaseImpl
import com.homelab.household.domain.usecase.impl.UnlockSecretSessionUseCaseImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The PIN reaches the data layer and is dropped there for now: nothing verifies it on either
 * side, which `docs/STAGE_5_SECRET_SESSION_LOCKING.md` §1 states outright. These tests pin the
 * only check that does exist today — that something was typed — and the pass-through the spec's
 * §5 needs when slice 5 sends the PIN to the hub.
 */
class SecretLockUseCasesTest {

    private val sessionRepo = mock<SessionRepository>()
    private val lockSecretSessionsUseCase = LockSecretSessionsUseCaseImpl(sessionRepo)
    private val unlockSecretSessionUseCase = UnlockSecretSessionUseCaseImpl(sessionRepo)

    @Test
    fun `GIVEN three secret conversations WHEN everything secret is locked THEN it reports three`() = runTest {
        // GIVEN
        everySuspend { sessionRepo.lockAllSecretSessions() } returns 3

        // WHEN
        val lockedCount = lockSecretSessionsUseCase()

        // THEN
        assertEquals(3, lockedCount)
    }

    @Test
    fun `GIVEN a locked conversation WHEN it is unlocked with a PIN THEN it opens`() = runTest {
        // GIVEN
        everySuspend { sessionRepo.unlockSecretSession("session-1", "1234") } returns true

        // WHEN
        val success = unlockSecretSessionUseCase("session-1", "1234")

        // THEN
        assertTrue(success)
    }

    @Test
    fun `GIVEN a conversation that was not locked WHEN it is unlocked THEN it says there was nothing to open`() = runTest {
        // GIVEN
        everySuspend { sessionRepo.unlockSecretSession("session-1", "1234") } returns false

        // WHEN
        val success = unlockSecretSessionUseCase("session-1", "1234")

        // THEN
        assertFalse(success)
    }

    @Test
    fun `GIVEN no PIN typed at all WHEN a conversation is unlocked THEN it is refused before the data layer is asked`() = runTest {
        // GIVEN
        val blank = "   "

        // WHEN
        assertFailsWith<ValidationException> { unlockSecretSessionUseCase("session-1", blank) }

        // THEN
        verifySuspend(VerifyMode.exactly(0)) { sessionRepo.unlockSecretSession("session-1", "1234") }
    }
}
