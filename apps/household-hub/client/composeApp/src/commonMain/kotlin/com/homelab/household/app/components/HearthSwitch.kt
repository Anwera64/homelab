package com.homelab.household.app.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/** A setting that is simply on or off — an invited member joining as an admin, a phone unlocking. */
@Composable
fun HearthSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val track = HearthTheme.size.touchTarget
    val thumb = HearthTheme.spacing.xl
    val travel = track - thumb - HearthTheme.spacing.sm
    val thumbOffset by animateDpAsState(if (checked) travel else HearthTheme.spacing.none)

    Box(
        modifier =
            modifier
                .width(track)
                .height(HearthTheme.spacing.xxl + HearthTheme.spacing.xs)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics { this.contentDescription = contentDescription }
                .background(if (checked) colors.primary else colors.outline, HearthShapes.pill)
                .padding(HearthTheme.spacing.xs),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = thumbOffset)
                    .size(thumb)
                    .background(colors.surface, CircleShape),
        )
    }
}
