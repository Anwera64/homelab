package com.homelab.household.app.components

import androidx.compose.runtime.Composable
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.theme.DayNightPreviews

/** Every chip variant side by side, so a colour that drifts from its neighbours is easy to spot. */
@DayNightPreviews
@Composable
private fun HearthChipVariantsPreview() {
    ComponentPreview {
        ChipVariant.entries.forEach { variant ->
            HearthChip(label = variant.name, variant = variant)
        }
    }
}

@DayNightPreviews
@Composable
private fun HearthChipWithIconPreview() {
    ComponentPreview {
        HearthChip(label = "Synced", variant = ChipVariant.Success, icon = HearthIcon.Synced)
        HearthChip(label = "Secret chat", variant = ChipVariant.Secret, icon = HearthIcon.SecretLocked)
    }
}
