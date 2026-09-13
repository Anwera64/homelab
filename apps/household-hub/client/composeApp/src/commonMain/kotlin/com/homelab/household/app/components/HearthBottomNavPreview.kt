package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme

/**
 * The bar edge to edge, as it sits on a screen. The canvas keeps room above it: the raised + stands
 * proud of the bar, and a preview cropped at the bar's top edge would cut it off.
 */
@DayNightPreviews
@Composable
private fun HearthBottomNavPreview() {
    HearthTheme {
        Box(modifier = Modifier.background(HearthTheme.colors.canvas).padding(top = HearthTheme.spacing.huge)) {
            HearthBottomNav(selected = NavTab.Chats, onSelect = {}, onNewChat = {})
        }
    }
}
