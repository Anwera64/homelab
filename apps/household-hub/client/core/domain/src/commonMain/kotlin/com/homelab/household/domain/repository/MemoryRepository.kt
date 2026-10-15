package com.homelab.household.domain.repository

import com.homelab.household.domain.model.MemoryPage

/** What the agents remember, as the memory page shows it. */
interface MemoryRepository {
    suspend fun getMemoryPage(): MemoryPage

    /** ✕: the hub retires the memory, so [restoreMemory] can bring it back. */
    suspend fun removeMemory(memoryId: String)

    suspend fun restoreMemory(memoryId: String)
}
