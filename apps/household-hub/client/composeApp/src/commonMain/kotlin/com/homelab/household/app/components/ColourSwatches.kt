package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.util.hexColor

/** How faint a colour someone else already wears looks. */
private const val TAKEN_ALPHA = 0.28f

/**
 * The Hygge palette, one circle each. Colour is how the schedule tells two people apart, so one
 * another member already wears is shown but cannot be chosen.
 */
@Composable
fun ColourSwatches(
    swatches: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    swatchDescription: @Composable (Int) -> String,
    modifier: Modifier = Modifier,
    taken: Set<String> = emptySet()
) {
    val colors = HearthTheme.colors

    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs)
    ) {
        swatches.forEachIndexed { index, hex ->
            val isSelected = hex == selected
            val isTaken = hex in taken
            val swatch = hexColor(hex, fallback = colors.primary)
            val description = swatchDescription(index)
            // The tap target is the full 48dp; the chosen swatch wears a ring in the gap around it.
            Box(
                modifier = Modifier
                    .size(HearthTheme.size.touchTarget)
                    .clip(CircleShape)
                    .selectable(
                        selected = isSelected,
                        enabled = !isTaken,
                        role = Role.RadioButton,
                        onClick = { onSelect(hex) }
                    )
                    .semantics { contentDescription = description }
                    .then(
                        if (isSelected) Modifier.border(HearthTheme.size.emphasis, swatch, CircleShape) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(HearthTheme.size.swatch)
                        .alpha(if (isTaken) TAKEN_ALPHA else 1f)
                        .background(swatch, CircleShape)
                )
            }
        }
    }
}
