package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homelab.household.app.components.ActionCard
import com.homelab.household.app.components.CardButtonRow
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

/**
 * A step that failed in a way the member can fix (canvas: ToolFailed): a red icon, what broke, that
 * nothing was written, and one button. The step's red line isn't repeated above it; it stays in
 * the fold.
 */
@Composable
fun ToolFixCard(
    fix: ToolFix,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val detail = stringResource(fix.detail)
    val caption = fix.outcome?.let { "$detail ${stringResource(it)}" } ?: detail

    ActionCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            // Nudged down to sit on the title's first line, as the canvas draws it.
            HearthIconImage(
                icon = fix.icon,
                contentDescription = null,
                size = HearthTheme.size.iconSm,
                tint = colors.error,
                modifier = Modifier.padding(top = ICON_NUDGE),
            )
            Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs)) {
                Text(text = stringResource(fix.title), style = type.bodyStrong, color = colors.textPrimary)
                Text(text = caption, style = type.caption, color = colors.textMuted)
            }
        }
        CardButtonRow {
            PrimaryButton(
                text = stringResource(fix.action),
                onClick = onFix,
                modifier = Modifier.weight(1f),
                lifted = false,
            )
        }
    }
}

private val ICON_NUDGE = 2.dp
