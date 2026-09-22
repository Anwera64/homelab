package com.homelab.household.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

@Composable
fun HearthChip(
    label: String,
    modifier: Modifier = Modifier,
    variant: ChipVariant = ChipVariant.Neutral,
    icon: HearthIcon? = null,
) {
    val colors = chipColors(variant, HearthTheme.colors)
    Surface(
        modifier = modifier,
        shape = HearthShapes.pill,
        color = colors.container,
        contentColor = colors.content,
        border = colors.border?.let { BorderStroke(HearthTheme.size.hairline, it) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = HearthTheme.spacing.md, vertical = HearthTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                HearthIconImage(icon = icon, contentDescription = null, size = HearthTheme.size.iconSm)
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
