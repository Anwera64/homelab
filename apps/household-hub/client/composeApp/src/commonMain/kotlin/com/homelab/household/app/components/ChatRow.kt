package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthTheme

/**
 * One conversation on the Chats list: the agent's face, the title, when it last moved, and the
 * last thing said in it.
 *
 * [agentAvatar] is null when the conversation has outlived its agent — purging one clears the
 * link. Chrome never uses emoji (design notes §3), so the fallback is a Hearth icon rather than an
 * invented emoji, and the row keeps its shape instead of collapsing. Slice 7 gives that case its
 * proper treatment; this only has to not look broken.
 */
@Composable
fun ChatRow(
    title: String,
    timestamp: String,
    preview: String?,
    agentAvatar: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = HearthTheme.size.touchTarget)
                .padding(vertical = HearthTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(HearthTheme.size.touchTarget)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = colors.ghostTint)),
            contentAlignment = Alignment.Center,
        ) {
            if (agentAvatar != null) {
                Text(text = agentAvatar, style = type.glyphLg)
            } else {
                HearthIconImage(
                    icon = HearthIcon.Chats,
                    contentDescription = null,
                    size = HearthTheme.size.iconLg,
                    tint = colors.textMuted,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = type.bodyStrong,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(text = timestamp, style = type.mono, color = colors.textMuted)
            }
            if (preview != null) {
                Text(
                    text = preview,
                    style = type.label,
                    color = colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
