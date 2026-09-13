package com.homelab.household.domain.usecase

fun interface ArchiveSessionUseCase {
    suspend operator fun invoke(sessionId: String)
}
