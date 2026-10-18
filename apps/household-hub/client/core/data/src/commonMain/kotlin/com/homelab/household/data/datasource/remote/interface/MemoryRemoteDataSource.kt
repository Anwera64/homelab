package com.homelab.household.data.datasource.remote.`interface`

import com.homelab.household.data.dto.ChangesToCheckDto
import com.homelab.household.data.dto.LookedOverDto
import com.homelab.household.data.dto.MemoryPageDto

/**
 * Everything the hub is asked about what the agents remember. Each function is one call, returning
 * the DTO the hub sent; `MemoryRepositoryImpl` maps them.
 */
interface MemoryRemoteDataSource {
    suspend fun getMemoryPage(): MemoryPageDto

    suspend fun removeMemory(memoryId: String)

    suspend fun restoreMemory(memoryId: String)

    suspend fun getChangesToCheck(): ChangesToCheckDto

    suspend fun answerChange(
        changeId: String,
        /** The hub's word for the answer: "keep_stored", "keep_both" or "use_new". */
        answer: String,
    )

    suspend fun undoChangeAnswer(changeId: String)

    suspend fun getLookedOver(sessionId: String): LookedOverDto

    suspend fun withdrawChange(changeId: String)

    suspend fun restoreChange(changeId: String)
}
