package com.homelab.household.domain.usecase

fun interface DeleteSessionUseCase {
    suspend operator fun invoke(sessionId: String)
}
