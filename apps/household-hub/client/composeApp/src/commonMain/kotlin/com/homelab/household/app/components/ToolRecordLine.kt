package com.homelab.household.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/**
 * The quiet line a tool leaves above an answer ("Checked your calendar").
 * It opens only when [onClick] is given, e.g. to show a search's sources.
 */
@Composable
fun ToolRecordLine(
    icon: HearthIcon,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = HearthTheme.colors.textMuted,
    onClick: (() -> Unit)? = null,
) {
    ToolRecordLine(
        icon = icon,
        text = AnnotatedString(text),
        modifier = modifier,
        iconTint = tint,
        textColor = tint,
        onClick = onClick,
    )
}

/**
 * The same line with words in more than one colour or with a link in them (#40): a failure in red
 * before a muted reason, or a page's title the reader can open. [trailing] sits at the row's end,
 * for a line that opens.
 */
@Composable
fun ToolRecordLine(
    icon: HearthIcon,
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    iconTint: Color = HearthTheme.colors.textMuted,
    textColor: Color = HearthTheme.colors.textMuted,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction =
        if (onClick != null) {
            Modifier
                .minimumInteractiveComponentSize()
                .clip(HearthShapes.item)
                .clickable(onClickLabel = onClickLabel, onClick = onClick)
        } else {
            Modifier
        }
    Row(
        modifier =
            modifier
                .then(interaction)
                .padding(horizontal = HearthTheme.spacing.xs, vertical = HearthTheme.spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HearthIconImage(icon = icon, contentDescription = null, size = HearthTheme.size.iconSm, tint = iconTint)
        Text(
            text = text,
            style = HearthTheme.typography.label,
            color = textColor,
            modifier = if (trailing != null) Modifier.weight(1f, fill = false) else Modifier,
        )
        trailing?.invoke()
    }
}
