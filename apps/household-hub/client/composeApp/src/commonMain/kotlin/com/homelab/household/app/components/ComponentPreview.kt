package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homelab.household.app.theme.HearthTheme

/**
 * The frame every component preview draws in: the theme, on its own canvas, with room around the
 * component. The tooling's background is white in both modes, so without the canvas a night
 * preview shows the component on the wrong ground.
 */
@Composable
internal fun ComponentPreview(content: @Composable ColumnScope.() -> Unit) {
    HearthTheme {
        Column(
            modifier =
                Modifier
                    .background(HearthTheme.colors.canvas)
                    .padding(HearthTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.lg),
            content = content,
        )
    }
}
