package com.homelab.household.app.screens.conversation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.app.screens.changes.previewChangeRow
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.presentation.memories.ChangeStatus
import com.homelab.household.presentation.memories.LocalDay
import com.homelab.household.presentation.memories.LocalTime
import com.homelab.household.presentation.memories.LookedOverUiState
import com.homelab.household.presentation.memories.MemoriesStatus
import com.homelab.household.presentation.memories.MemoryAge
import com.homelab.household.presentation.memories.MemoryRow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** "Remembered from this chat" in each state the canvas draws. `LookedOverSheetTest` renders them all. */
@OptIn(ExperimentalTime::class)
class LookedOverUiStateProvider : PreviewParameterProvider<LookedOverUiState> {
    private fun remembered(
        id: String,
        content: String,
        household: Boolean = false,
        until: LocalDay? = null,
        removed: Boolean = false,
    ) = MemoryRow(
        item =
            MemoryItem(
                id = id,
                content = content,
                scope = if (household) MemoryScope.HOUSEHOLD else MemoryScope.PERSONAL,
                origin = MemoryOrigin.Reflection,
                createdAt = Instant.parse("2026-10-16T10:52:00Z"),
                expiresAt = null,
                speakerId = "emma",
                speakerName = "Emma Larsson",
                isMine = true,
                agentName = "Planner",
                agentAvatar = "🗓️",
                canRemove = true,
            ),
        age = MemoryAge.Today,
        until = until,
        removed = removed,
    )

    private val change =
        previewChangeRow("c1", "Is vegetarian, but cooks fish for Liam.", "Eats fish again since September.")
    private val liams = previewChangeRow("c3", "Works late on Thursdays.", "Now works late on Fridays.", mine = false)
    private val rows =
        listOf(
            remembered("r1", "Studio Jury on Friday 17 October, 09:00.", until = LocalDay(day = 17, month = 10)),
            remembered("r2", "Dinner together on Saturday 18 October, 20:00.", household = true),
        )
    private val sheet =
        LookedOverUiState(
            status = MemoriesStatus.Ready,
            at = LocalTime(hour = 10, minute = 52),
            toCheck = listOf(change),
            waiting = listOf(liams),
            remembered = rows,
        )

    override val values =
        sequenceOf(
            sheet,
            // Canvas: MemChatSheetAnswered. Answered in place, and one memory taken back.
            sheet.copy(
                toCheck = listOf(change.copy(status = ChangeStatus.Done(ProposalAnswer.UseNew))),
                waiting = emptyList(),
                remembered = listOf(rows[0].copy(removed = true), rows[1]),
            ),
            sheet.copy(toCheck = listOf(change.copy(status = ChangeStatus.Saving(ProposalAnswer.KeepBoth)))),
            LookedOverUiState(status = MemoriesStatus.Loading),
            LookedOverUiState(status = MemoriesStatus.Unreachable),
        )
}
