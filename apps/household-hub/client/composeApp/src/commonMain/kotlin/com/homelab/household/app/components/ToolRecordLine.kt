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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onClick: (() -> Unit)? = null
) {
    val interaction = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .clip(HearthShapes.item)
            .clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier.then(interaction).padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HearthIconImage(icon = icon, contentDescription = null, size = 14.dp, tint = tint)
        Text(text = text, fontFamily = HearthTheme.fonts.inter, fontSize = 12.sp, color = tint)
    }
}
