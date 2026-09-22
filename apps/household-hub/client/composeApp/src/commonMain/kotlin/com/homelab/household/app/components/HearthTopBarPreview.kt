package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight

// Edge to edge, as it sits at the top of a screen: the bar paints its own canvas.

/** Just the way back. */
@PreviewDayNight
@Composable
private fun HearthTopBarWithoutTitlePreview() {
    HearthTheme {
        HearthTopBar(onBack = {}, backDescription = "Back")
    }
}

/** The way back, with the screen's name beside it. */
@PreviewDayNight
@Composable
private fun HearthTopBarWithTitlePreview() {
    HearthTheme {
        HearthTopBar(onBack = {}, backDescription = "Back", title = "Members")
    }
}
