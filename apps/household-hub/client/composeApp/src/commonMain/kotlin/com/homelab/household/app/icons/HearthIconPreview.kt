package com.homelab.household.app.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme

/**
 * The whole icon set, each drawn resting and active side by side, named by its sheet token. Put
 * next to the sheet, a path copied wrong or a stroke that doesn't thicken stands out.
 */
@OptIn(ExperimentalLayoutApi::class)
@DayNightPreviews
@Composable
private fun HearthIconSetPreview() {
    HearthTheme {
        val cellWidth = HearthTheme.size.readingWidth / 3
        Column(
            modifier = Modifier
                .background(HearthTheme.colors.canvas)
                .verticalScroll(rememberScrollState())
                .padding(HearthTheme.spacing.xl)
        ) {
            // Three cells to a row: the grid is as wide as the reading column, and cells carry no gap.
            FlowRow(
                modifier = Modifier.widthIn(max = HearthTheme.size.readingWidth),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg)
            ) {
                HearthIcon.entries.forEach { icon ->
                    IconCell(icon = icon, modifier = Modifier.width(cellWidth))
                }
            }
        }
    }
}

@Composable
private fun IconCell(icon: HearthIcon, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
            HearthIconImage(
                icon = icon,
                contentDescription = null,
                active = false,
                size = HearthTheme.size.iconLg,
                tint = HearthTheme.colors.textPrimary
            )
            HearthIconImage(
                icon = icon,
                contentDescription = null,
                active = true,
                size = HearthTheme.size.iconLg,
                tint = HearthTheme.colors.textPrimary
            )
        }
        Text(text = icon.token, style = HearthTheme.typography.monoSm, color = HearthTheme.colors.textMuted)
    }
}
