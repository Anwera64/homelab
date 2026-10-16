package com.homelab.household.presentation.memories

/** The sheet "Remembered from this chat" (canvas MemChatSheet). [at] is when the chat was looked over. */
data class LookedOverUiState(
    val status: MemoriesStatus = MemoriesStatus.Loading,
    val at: LocalTime? = null,
    val toCheck: List<ChangeRow> = emptyList(),
    val waiting: List<ChangeRow> = emptyList(),
    val remembered: List<MemoryRow> = emptyList(),
    /** The last ✕ or Undo the hub didn't take, said in a line until the next tap. */
    val failure: MemoryActionFailure? = null,
) {
    val openCount: Int get() = toCheck.openCount()

    /** A removed row still shows, with Undo, but no longer counts. */
    val rememberedCount: Int get() = remembered.count { !it.removed }

    val waitingByAnswerer: List<Pair<String, List<ChangeRow>>> get() = waiting.byAnswerer()
}
