package com.homelab.household.app.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Draws a [HearthIcon] tinted with the current content colour. */
@Composable
fun HearthIconImage(
    icon: HearthIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current
) {
    Icon(
        imageVector = icon.vector(active),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}
