package com.homelab.household.app.screens.memories

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.presentation.memories.LocalDay
import com.homelab.household.presentation.memories.MemoriesStatus
import com.homelab.household.presentation.memories.MemoriesUiState
import com.homelab.household.presentation.memories.MemoryActionFailure
import com.homelab.household.presentation.memories.MemoryAge
import com.homelab.household.presentation.memories.MemoryRow
import com.homelab.household.presentation.memories.MemoryTab
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The memory page in each state the canvas draws. `MemoriesScreenTest` renders them all. */
@OptIn(ExperimentalTime::class)
class MemoriesUiStateProvider : PreviewParameterProvider<MemoriesUiState> {
    private fun row(
        id: String,
        content: String,
        agent: Pair<String, String> = "🏡" to "Coordinator",
        age: MemoryAge = MemoryAge.Today,
        household: Boolean = false,
        mine: Boolean = true,
        youAsked: Boolean = false,
        until: LocalDay? = null,
        removed: Boolean = false,
    ) = MemoryRow(
        item =
            MemoryItem(
                id = id,
                content = content,
                scope = if (household) MemoryScope.HOUSEHOLD else MemoryScope.PERSONAL,
                origin = if (youAsked) MemoryOrigin.Tool else MemoryOrigin.Reflection,
                createdAt = Instant.parse("2026-10-15T09:00:00Z"),
                expiresAt = null,
                speakerId = if (mine) "emma" else "liam",
                speakerName = if (mine) "Emma Larsson" else "Liam",
                isMine = mine,
                agentName = agent.second,
                agentAvatar = agent.first,
                canRemove = mine,
            ),
        age = age,
        until = until,
        removed = removed,
    )

    private val researcher = "📚" to "Researcher"

    private val yours =
        listOf(
            row("p1", "Prefers dinner after 20:00 on weekdays."),
            row(
                "p2",
                "Studio Jury on Friday 17 October, 09:00.",
                agent = researcher,
                age = MemoryAge.Weekday(isoDay = 7),
                until = LocalDay(day = 17, month = 10),
            ),
            row("p3", "Is vegetarian, but cooks fish for Liam.", age = MemoryAge.LastWeek, youAsked = true),
            row("p4", "Studies architecture at UPC Barcelona.", agent = researcher, age = MemoryAge.Month(3)),
        )

    private val household =
        listOf(
            row(
                "h1",
                "Dinner together on Saturday 18 October, 20:00.",
                household = true,
                until = LocalDay(day = 18, month = 10),
            ),
            row(
                "h2",
                "Sam is vegetarian and doesn’t eat fish. He eats eggs and cheese, avoids gelatine, takes oat milk " +
                    "in his coffee, and doesn’t like mushrooms or anything very spicy, except his grandmother’s curry.",
                agent = "🗓️" to "Planner",
                age = MemoryAge.Month(3),
                household = true,
                mine = false,
            ),
            row(
                "h3",
                "The boiler service is booked yearly in October.",
                age = MemoryAge.LastWeek,
                household = true,
                mine = false,
            ),
        )

    private val ready = MemoriesUiState(status = MemoriesStatus.Ready, yours = yours, household = household)

    private val named =
        listOf(
            "Yours" to ready,
            "Household" to ready.copy(tab = MemoryTab.Household),
            "One removed, with Undo" to
                ready.copy(yours = yours.mapIndexed { i, it -> if (i == 1) it.copy(removed = true) else it }),
            "A removal that didn't reach the hub" to ready.copy(failure = MemoryActionFailure.RemoveUnreachable),
            "Arriving" to MemoriesUiState(status = MemoriesStatus.Loading),
            "Can't reach the hub" to MemoriesUiState(status = MemoriesStatus.Unreachable),
            "Hub error" to MemoriesUiState(status = MemoriesStatus.Failed),
            "Nothing remembered" to MemoriesUiState(status = MemoriesStatus.Ready),
        )

    override val values: Sequence<MemoriesUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
