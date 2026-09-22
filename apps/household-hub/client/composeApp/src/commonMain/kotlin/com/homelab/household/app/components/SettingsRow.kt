package com.homelab.household.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthTheme

/** How faint a row that cannot be opened looks. */
private const val CLOSED_ALPHA = 0.5f

/**
 * One row of a grouped list: a name, sometimes a line under it, and a chevron.
 *
 * A row that cannot be opened is the one place dimming is right — it says "you can't have this",
 * and the caption says why (design notes §2). Everything readable keeps full contrast.
 */
@Composable
fun SettingsRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: HearthIcon? = null,
    enabled: Boolean = true,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val ink = if (enabled) colors.textPrimary else colors.textMuted.copy(alpha = CLOSED_ALPHA)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = HearthTheme.size.touchTarget)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = HearthTheme.spacing.lg, vertical = HearthTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
    ) {
        if (icon != null) {
            HearthIconImage(icon = icon, contentDescription = null, size = HearthTheme.size.iconLg, tint = ink)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xxs),
        ) {
            Text(label, style = type.bodyStrong, color = ink)
            if (caption != null) {
                Text(caption, style = type.caption, color = colors.textMuted)
            }
        }
        when {
            trailing != null -> {
                trailing()
            }

            enabled -> {
                HearthIconImage(
                    icon = HearthIcon.ChevronRight,
                    contentDescription = null,
                    size = HearthTheme.size.iconMd,
                    tint = colors.textMuted,
                )
            }
        }
    }
}
