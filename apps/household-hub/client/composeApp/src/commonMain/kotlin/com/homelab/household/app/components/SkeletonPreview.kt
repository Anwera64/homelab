package com.homelab.household.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.theme.PreviewDayNight

/**
 * A member row, waiting. Previews keep `DefaultMotion`, so this is the breath as it actually runs:
 * the lines are deliberately uneven, and each row breathes a beat behind the one above, which is
 * what makes it read as a wait rather than a grid.
 */
@PreviewDayNight
@Composable
private fun SkeletonPreview() {
    ComponentPreview {
        SkeletonGroup {
            repeat(3) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonCircle(position = row)
                    SkeletonBlock(widthFraction = 0.6f, position = row)
                }
            }
        }
    }
}
