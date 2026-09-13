package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme

// Edge to edge, as it sits under a conversation: the bar paints its own surface.

/** Nothing typed yet, so the placeholder shows. */
@DayNightPreviews
@Composable
private fun MessageComposerEmptyPreview() {
    HearthTheme {
        MessageComposer(value = "", onValueChange = {}, onSend = {}, placeholder = "Message the household")
    }
}

/** A message on its way. */
@DayNightPreviews
@Composable
private fun MessageComposerWithTextPreview() {
    HearthTheme {
        MessageComposer(
            value = "Can you add milk and eggs to the shopping list?",
            onValueChange = {},
            onSend = {},
            placeholder = "Message the household"
        )
    }
}
