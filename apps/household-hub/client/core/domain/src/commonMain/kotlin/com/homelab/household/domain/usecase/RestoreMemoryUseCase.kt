package com.homelab.household.domain.usecase

/** Undo after ✕: the memory is kept again. */
fun interface RestoreMemoryUseCase {
    suspend operator fun invoke(memoryId: String)
}
