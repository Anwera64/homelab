package com.homelab.household.domain.usecase

fun interface LockSecretSessionsUseCase {
    suspend operator fun invoke(): Int
}
