package com.homelab.household.app.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme

/**
 * A member row, waiting. Previews keep `DefaultMotion`, so this is the breath as it actually runs;
 * the lines are deliberately uneven, which is what makes it read as a wait rather than a grid.
 */
@DayNightPreviews
@Composable
private fun SkeletonPreview() {
    ComponentPreview {
        SkeletonGroup {
            repeat(3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonCircle()
                    SkeletonBlock(widthFraction = 0.6f)
                }
            }
        }
    }
}
