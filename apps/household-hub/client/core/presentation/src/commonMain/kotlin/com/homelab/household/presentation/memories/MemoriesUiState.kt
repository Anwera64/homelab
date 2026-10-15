package com.homelab.household.presentation.memories

import com.homelab.household.domain.model.MemoryItem

/** The memory page (canvas "Memory (slice 5)"): two tabs of memories, each removable in place if it's yours. */
data class MemoriesUiState(
    val status: MemoriesStatus = MemoriesStatus.Loading,
    val tab: MemoryTab = MemoryTab.Yours,
    val yours: List<MemoryRow> = emptyList(),
    val household: List<MemoryRow> = emptyList(),
    /** The last ✕ or Undo the hub didn't take, said in a line until the next tap. */
    val failure: MemoryActionFailure? = null,
) {
    /** A removed row still shows, with Undo, but no longer counts. */
    val yoursCount: Int get() = yours.count { !it.removed }
    val householdCount: Int get() = household.count { !it.removed }
}

enum class MemoriesStatus {
    Loading,
    Ready,
    Unreachable,
    Failed,
}

enum class MemoryTab {
    Yours,
    Household,
}

enum class MemoryActionFailure {
    RemoveUnreachable,
    RemoveFailed,
    UndoUnreachable,
    UndoFailed,
}

/** One memory as its row draws it. [removed] turns the row into "Removed · Undo" until the page is left. */
data class MemoryRow(
    val item: MemoryItem,
    val age: MemoryAge,
    val until: LocalDay?,
    val removed: Boolean = false,
)
