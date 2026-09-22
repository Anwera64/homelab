package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.theme.DayNightPreviews

/** A screen you're expected to act on, with its action. */
@DayNightPreviews
@Composable
private fun EmptyStateWithActionPreview() {
    ComponentPreview {
        EmptyState(
            icon = HearthIcon.Chats,
            title = "No chats yet",
            line = "Conversations you start will appear here.",
            action = EmptyStateAction(label = "Start a chat", onClick = {}),
        )
    }
}

/** A screen that fills itself in, so no action. */
@DayNightPreviews
@Composable
private fun EmptyStateWithoutActionPreview() {
    ComponentPreview {
        EmptyState(
            icon = HearthIcon.Schedule,
            title = "Nothing scheduled",
            line = "Plans the household shares will show up here.",
        )
    }
}
