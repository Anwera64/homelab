package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import com.homelab.household.app.theme.PreviewDayNight

/** The three widths, running. Previews keep `DefaultMotion`; only tests turn the motion off. */
@PreviewDayNight
@Composable
private fun HearthProgressBarPreview() {
    ComponentPreview {
        HearthProgressBar()
        HearthProgressBar(width = HearthProgressBarWidth.FullBleed)
        HearthProgressBar(width = HearthProgressBarWidth.Inset)
    }
}
