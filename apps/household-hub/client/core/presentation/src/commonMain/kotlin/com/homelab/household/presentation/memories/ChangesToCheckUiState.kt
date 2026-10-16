package com.homelab.household.presentation.memories

/** Changes to check (canvas MemProposals): your cards to answer, then the ones waiting for someone else. */
data class ChangesToCheckUiState(
    val status: MemoriesStatus = MemoriesStatus.Loading,
    val toCheck: List<ChangeRow> = emptyList(),
    val waiting: List<ChangeRow> = emptyList(),
) {
    /** "2 changes to check"; at 0 the page says "Nothing left to check". */
    val openCount: Int get() = toCheck.openCount()

    val waitingByAnswerer: List<Pair<String, List<ChangeRow>>> get() = waiting.byAnswerer()
}
