package com.homelab.household.domain.repository

import com.homelab.household.domain.model.ChangesToCheck
import com.homelab.household.domain.model.LookedOver
import com.homelab.household.domain.model.MemoryPage
import com.homelab.household.domain.model.ProposalAnswer

/** What the agents remember, as the memory page shows it, and the changes waiting to be checked. */
interface MemoryRepository {
    suspend fun getMemoryPage(): MemoryPage

    /** ✕: the hub retires the memory, so [restoreMemory] can bring it back. */
    suspend fun removeMemory(memoryId: String)

    suspend fun restoreMemory(memoryId: String)

    suspend fun getChangesToCheck(): ChangesToCheck

    suspend fun answerChange(
        changeId: String,
        answer: ProposalAnswer,
    )

    /** Undo after answering: the hub puts everything back and the change is open again. */
    suspend fun undoChangeAnswer(changeId: String)

    /** A chat's sheet, "Remembered from this chat". */
    suspend fun getLookedOver(sessionId: String): LookedOver

    /** ✕ on a waiting card: the sentence stops waiting, so [restoreChange] can bring it back. */
    suspend fun withdrawChange(changeId: String)

    suspend fun restoreChange(changeId: String)
}
