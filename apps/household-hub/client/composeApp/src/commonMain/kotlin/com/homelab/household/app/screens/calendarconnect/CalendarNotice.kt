package com.homelab.household.app.screens.calendarconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/**
 * A calendar screen's notice: the warning before a password field, or once something was refused,
 * what went wrong and the steps to fix it.
 */
@Composable
internal fun CalendarNotice(
    title: String,
    detail: String,
    icon: HearthIcon,
    background: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    steps: List<String> = emptyList(),
) {
    val type = HearthTheme.typography
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(background, HearthShapes.bento)
                .padding(HearthTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HearthIconImage(icon = icon, contentDescription = null, size = HearthTheme.size.iconMd, tint = ink)
            Text(title, style = type.bodyStrong, color = ink)
        }
        Text(detail, style = if (steps.isEmpty()) type.caption else type.label, color = ink)
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm)) {
                Text("${index + 1}", style = type.monoSm, color = ink)
                Text(step, style = type.caption, color = ink)
            }
        }
    }
}
