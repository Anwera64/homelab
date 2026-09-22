package com.homelab.household.app.screens.chats

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.ConversationSession
import com.homelab.household.presentation.chats.ChatsUiState

/** The Chats list in each state worth drawing. `ChatsScreenTest` renders them all. */
class ChatsUiStateProvider : PreviewParameterProvider<ChatsUiState> {
    private fun chat(
        id: String,
        title: String,
        preview: String,
        avatar: String = "🏡",
        at: String = "10m",
    ) = ConversationSession(
        id = id,
        userId = "u-1",
        title = title,
        agentName = if (avatar == "🏡") "Home & Life Coordinator" else "Academic & Document Researcher",
        agentAvatar = avatar,
        lastMessagePreview = preview,
        updatedAt = at,
    )

    private val dinner =
        chat("s-1", "Saturday dinner", "Added. I've let the household know you're both out Saturday evening.")
    private val papers =
        chat(
            "s-2",
            "Jury panel references",
            "I pulled three papers on adaptive reuse — the Lacaton & Vassal one is closest.",
            avatar = "📚",
            at = "Tue",
        )
    private val groceries =
        chat(
            "s-3",
            "Groceries for the week",
            "Liam's picking up the veg box Thursday, so skip the market run.",
            at = "Mon",
        )

    private val all = listOf(dinner, papers, groceries)

    private val named =
        listOf(
            "The list" to ChatsUiState(visible = all, loaded = true, total = all.size),
            "Searching" to
                ChatsUiState(query = "dinner", visible = listOf(dinner), loaded = true, total = all.size),
            "Nothing matched" to
                ChatsUiState(query = "boiler", visible = emptyList(), loaded = true, total = all.size),
            // The one empty screen with a button: it is the only one you are expected to act on.
            "Never chatted" to ChatsUiState(visible = emptyList(), loaded = true, total = 0),
            "Arriving" to ChatsUiState(isLoading = true),
            "Coming back" to ChatsUiState(isLoading = true, visible = all, loaded = true, total = all.size),
            "Hub unreachable" to ChatsUiState(errorMessage = "Can't reach your hub"),
            // A conversation that outlived its agent: no emoji to draw, and it must not collapse.
            "An agent that is gone" to
                ChatsUiState(
                    visible =
                        listOf(
                            ConversationSession(
                                id = "s-9",
                                userId = "u-1",
                                title = "Is the 5080 worth it?",
                                lastMessagePreview = "That settles it, thanks",
                                updatedAt = "Last week",
                            ),
                        ),
                    loaded = true,
                    total = 1,
                ),
        )

    override val values: Sequence<ChatsUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
