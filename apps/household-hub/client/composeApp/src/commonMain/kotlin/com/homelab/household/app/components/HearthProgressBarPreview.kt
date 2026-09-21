package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import com.homelab.household.app.theme.DayNightPreviews

/** The three widths, running. Previews keep `DefaultMotion`; only tests turn the motion off. */
@DayNightPreviews
@Composable
private fun HearthProgressBarPreview() {
    ComponentPreview {
        HearthProgressBar()
        HearthProgressBar(width = HearthProgressBarWidth.FullBleed)
        HearthProgressBar(width = HearthProgressBarWidth.Inset)
    }
}
