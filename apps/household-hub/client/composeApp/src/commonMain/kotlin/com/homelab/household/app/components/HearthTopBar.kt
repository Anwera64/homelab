package com.homelab.household.app.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthTheme

/**
 * A back arrow on the canvas, with an optional [title] beside it. The header of any screen you
 * back out of; [backDescription] is what a screen reader says for the arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HearthTopBar(
    onBack: () -> Unit,
    backDescription: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val colors = HearthTheme.colors

    TopAppBar(
        title = {
            if (title != null) {
                Text(text = title, style = HearthTheme.typography.heading, color = colors.textPrimary)
            }
        },
        modifier = modifier,
        navigationIcon = {
            // The bar already insets its icon by 4dp; this brings the arrow to the 16dp the canvas draws.
            IconButton(onClick = onBack, modifier = Modifier.padding(start = HearthTheme.spacing.md)) {
                HearthIconImage(icon = HearthIcon.Back, contentDescription = backDescription, tint = colors.textMuted)
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colors.canvas,
                scrolledContainerColor = colors.canvas,
            ),
    )
}
