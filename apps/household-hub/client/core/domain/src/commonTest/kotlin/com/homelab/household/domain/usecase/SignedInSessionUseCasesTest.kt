package com.homelab.household.domain.usecase

import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.repository.AuthRepository
import com.homelab.household.domain.usecase.impl.ObserveSignedOutUseCaseImpl
import com.homelab.household.domain.usecase.impl.RenewSessionUseCaseImpl
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Staying signed in on this phone: renewing the token, and hearing when the hub stopped accepting it. */
class SignedInSessionUseCasesTest {
    private val authRepo = mock<AuthRepository>()

    @Test
    fun `GIVEN the hub signing this phone out WHEN the sign-out is observed THEN the watcher is told once`() =
        runTest {
            every { authRepo.observeSignedOut() } returns flowOf(Unit)

            assertEquals(listOf(Unit), ObserveSignedOutUseCaseImpl(authRepo)().toList())
        }

    @Test
    fun `GIVEN a hub ready to renew WHEN the session is renewed THEN the hub is asked for a fresh token once`() =
        runTest {
            everySuspend { authRepo.refreshToken() } returns "fresh-token"

            RenewSessionUseCaseImpl(authRepo)()

            verifySuspend(VerifyMode.exactly(1)) { authRepo.refreshToken() }
        }

    @Test
    fun `GIVEN a hub that is unreachable WHEN the session is renewed THEN nothing is thrown and the token is kept`() =
        runTest {
            everySuspend { authRepo.refreshToken() } throws ServerOfflineException()

            RenewSessionUseCaseImpl(authRepo)()
        }

    @Test
    fun `GIVEN a hub that no longer accepts the token WHEN the session is renewed THEN nothing is thrown because the sign-out signal handles it`() =
        runTest {
            everySuspend { authRepo.refreshToken() } throws UnauthorizedException("Token no longer accepted")

            RenewSessionUseCaseImpl(authRepo)()
        }
}
