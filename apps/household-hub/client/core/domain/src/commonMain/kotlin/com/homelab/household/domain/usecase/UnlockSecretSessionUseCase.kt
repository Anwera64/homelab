package com.homelab.household.domain.usecase

fun interface UnlockSecretSessionUseCase {
    suspend operator fun invoke(
        sessionId: String,
        pinOrPassword: String,
    ): Boolean
}
