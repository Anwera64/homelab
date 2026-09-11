package com.homelab.household.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthColors
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

enum class ChipVariant { Neutral, Primary, Secondary, Error, Success, Secret }

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

@Composable
fun HearthChip(
    label: String,
    modifier: Modifier = Modifier,
    variant: ChipVariant = ChipVariant.Neutral,
    icon: HearthIcon? = null
) {
    val colors = chipColors(variant, HearthTheme.colors)
    Surface(
        modifier = modifier,
        shape = HearthShapes.pill,
        color = colors.container,
        contentColor = colors.content,
        border = colors.border?.let { BorderStroke(1.dp, it) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                HearthIconImage(icon = icon, contentDescription = null, size = 14.dp)
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
