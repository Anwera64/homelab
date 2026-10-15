package com.homelab.household.domain.usecase

/** ✕ on one of your memories. It can be undone with [RestoreMemoryUseCase]. */
fun interface RemoveMemoryUseCase {
    suspend operator fun invoke(memoryId: String)
}
