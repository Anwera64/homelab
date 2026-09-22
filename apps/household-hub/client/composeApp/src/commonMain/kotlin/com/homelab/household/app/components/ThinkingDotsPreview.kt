package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import com.homelab.household.app.theme.PreviewDayNight

/** The wait before an answer's first word. Keeps `DefaultMotion`, so interactive mode shows the wave. */
@PreviewDayNight
@Composable
private fun ThinkingDotsPreview() {
    ComponentPreview {
        ThinkingDots()
    }
}
