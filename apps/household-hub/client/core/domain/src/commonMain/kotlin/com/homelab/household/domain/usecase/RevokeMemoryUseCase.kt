package com.homelab.household.domain.usecase

fun interface RevokeMemoryUseCase {
    suspend operator fun invoke(memoryId: String)
}
