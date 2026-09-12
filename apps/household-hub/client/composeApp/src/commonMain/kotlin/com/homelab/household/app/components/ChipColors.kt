package com.homelab.household.app.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.homelab.household.app.theme.HearthColors

@Immutable
data class ChipColors(val container: Color, val content: Color, val border: Color?)

/** Secret is a ghost — tint and hairline over the accent, never a fill — tuned per palette. */
fun chipColors(variant: ChipVariant, palette: HearthColors): ChipColors = with(palette) {
    when (variant) {
        ChipVariant.Neutral -> ChipColors(surfaceAlt, textMuted, outline)
        ChipVariant.Primary -> ChipColors(primaryContainer, onPrimaryContainer, null)
        ChipVariant.Secondary -> ChipColors(secondaryContainer, onSecondaryContainer, null)
        ChipVariant.Error -> ChipColors(errorContainer, onErrorContainer, null)
        ChipVariant.Success -> ChipColors(successContainer, onSuccessContainer, null)
        ChipVariant.Secret -> ChipColors(
            container = secret.copy(alpha = ghostTint),
            content = secret,
            border = secret.copy(alpha = ghostEdge)
        )
    }
}
