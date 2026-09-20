package com.homelab.household.domain.usecase

/**
 * Swaps the kept token for a fresh one when the app starts signed in, so a phone in use never
 * reaches the end of its token. Never fails: offline keeps the token it has, and a token the hub
 * refused is already being handled as a sign-out.
 */
fun interface RenewSessionUseCase {
    suspend operator fun invoke()
}
