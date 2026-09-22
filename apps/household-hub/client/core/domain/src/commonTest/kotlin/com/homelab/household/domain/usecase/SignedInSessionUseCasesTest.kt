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
    fun a_sign_out_from_the_hub_reaches_whoever_is_watching() =
        runTest {
            every { authRepo.observeSignedOut() } returns flowOf(Unit)

            assertEquals(listOf(Unit), ObserveSignedOutUseCaseImpl(authRepo)().toList())
        }

    @Test
    fun renewing_asks_the_hub_for_a_fresh_token() =
        runTest {
            everySuspend { authRepo.refreshToken() } returns "fresh-token"

            RenewSessionUseCaseImpl(authRepo)()

            verifySuspend(VerifyMode.exactly(1)) { authRepo.refreshToken() }
        }

    @Test
    fun renewing_with_the_hub_unreachable_quietly_keeps_the_token_it_has() =
        runTest {
            everySuspend { authRepo.refreshToken() } throws ServerOfflineException()

            RenewSessionUseCaseImpl(authRepo)()
        }

    @Test
    fun renewing_a_token_the_hub_refused_leaves_the_sign_out_to_the_signal() =
        runTest {
            everySuspend { authRepo.refreshToken() } throws UnauthorizedException("Token no longer accepted")

            RenewSessionUseCaseImpl(authRepo)()
        }
}
