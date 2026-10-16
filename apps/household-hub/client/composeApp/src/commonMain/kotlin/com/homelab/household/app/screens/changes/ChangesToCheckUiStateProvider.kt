package com.homelab.household.app.screens.changes

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.ChangeCard
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.presentation.memories.ChangeRow
import com.homelab.household.presentation.memories.ChangeStatus
import com.homelab.household.presentation.memories.ChangesToCheckUiState
import com.homelab.household.presentation.memories.LocalTime
import com.homelab.household.presentation.memories.MemoriesStatus
import com.homelab.household.presentation.memories.MemoryAge
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** A change as the previews draw it: stored in March, read in "Dinner ideas" today at 10:42. */
@OptIn(ExperimentalTime::class)
internal fun previewChangeRow(
    id: String,
    stored: String,
    read: String,
    mine: Boolean = true,
    household: Boolean = false,
    title: String? = "Dinner ideas",
    status: ChangeStatus = ChangeStatus.Open(),
    readAge: MemoryAge = MemoryAge.Today,
) = ChangeRow(
    card =
        ChangeCard(
            id = id,
            content = read,
            stored =
                MemoryItem(
                    id = "s-$id",
                    content = stored,
                    scope = if (household) MemoryScope.HOUSEHOLD else MemoryScope.PERSONAL,
                    origin = MemoryOrigin.Reflection,
                    createdAt = Instant.parse("2026-03-10T09:00:00Z"),
                    expiresAt = null,
                    speakerId = if (mine) "emma" else "liam",
                    speakerName = if (mine) "Emma Larsson" else "Liam",
                    isMine = mine,
                    agentName = "Planner",
                    agentAvatar = "🗓️",
                    canRemove = mine,
                ),
            canAnswer = mine,
            answererName = if (mine) "Emma" else "Liam",
            sourceTitle = title,
            sourceOwnerName = "Emma",
            readAt = Instant.parse("2026-10-16T10:42:00Z"),
        ),
    storedAge = MemoryAge.Month(3),
    readAge = readAge,
    readTime = LocalTime(hour = 10, minute = 42),
    status = status,
)

/** Changes to check in each state the canvas draws. `ChangesToCheckScreenTest` renders them all. */
@OptIn(ExperimentalTime::class)
class ChangesToCheckUiStateProvider : PreviewParameterProvider<ChangesToCheckUiState> {
    private val vegetarian =
        previewChangeRow("c1", "Is vegetarian, but cooks fish for Liam.", "Eats fish again since September.")
    private val dinner =
        previewChangeRow(
            "c2",
            "Dinner together on Saturdays, 20:00.",
            "Dinner together on Sundays now, 19:30.",
            household = true,
            title = null,
            readAge = MemoryAge.Yesterday,
        )
    private val liams =
        previewChangeRow("c3", "Works late on Thursdays.", "Now works late on Fridays.", mine = false)

    private val long =
        previewChangeRow(
            "c4",
            "Sam is vegetarian and doesn’t eat fish. He eats eggs and cheese, avoids gelatine, takes oat milk " +
                "in his coffee, and doesn’t like mushrooms or anything very spicy, except his grandmother’s curry.",
            "Sam eats fish now, but still no mushrooms, nothing very spicy, and oat milk in his coffee, " +
                "except at his grandmother’s, where he has whatever curry she makes.",
            household = true,
        )

    override val values =
        sequenceOf(
            ChangesToCheckUiState(status = MemoriesStatus.Ready, toCheck = listOf(vegetarian, dinner)),
            ChangesToCheckUiState(status = MemoriesStatus.Ready, toCheck = listOf(vegetarian), waiting = listOf(liams)),
            ChangesToCheckUiState(
                status = MemoriesStatus.Ready,
                toCheck = listOf(vegetarian.copy(status = ChangeStatus.Saving(ProposalAnswer.UseNew)), dinner),
            ),
            ChangesToCheckUiState(
                status = MemoriesStatus.Ready,
                toCheck = listOf(vegetarian.copy(status = ChangeStatus.Open(failed = true)), dinner),
            ),
            ChangesToCheckUiState(
                status = MemoriesStatus.Ready,
                toCheck =
                    listOf(
                        vegetarian.copy(status = ChangeStatus.Done(ProposalAnswer.UseNew)),
                        dinner.copy(status = ChangeStatus.Done(ProposalAnswer.KeepStored)),
                    ),
            ),
            ChangesToCheckUiState(status = MemoriesStatus.Ready, toCheck = listOf(long)),
            ChangesToCheckUiState(status = MemoriesStatus.Loading),
            ChangesToCheckUiState(status = MemoriesStatus.Unreachable),
        )
}
