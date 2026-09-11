package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthTheme

const val EmptyStateIconTag = "EmptyStateIcon"

class EmptyStateAction(val label: String, val onClick: () -> Unit)

/**
 * The one empty-state pattern: the screen's own icon on a soft tile, a plain title and one line
 * on what will appear here. Only screens where you're expected to act get an [action].
 */
@Composable
fun EmptyState(
    icon: HearthIcon,
    title: String,
    line: String,
    modifier: Modifier = Modifier,
    action: EmptyStateAction? = null
) {
    val colors = HearthTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(colors.outlineSoft, RoundedCornerShape(22.dp))
                .testTag(EmptyStateIconTag),
            contentAlignment = Alignment.Center
        ) {
            HearthIconImage(icon = icon, contentDescription = null, size = 28.dp, tint = colors.textMuted)
        }
        Text(
            text = title,
            fontFamily = HearthTheme.fonts.outfit,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = line,
            modifier = Modifier.widthIn(max = 290.dp),
            fontFamily = HearthTheme.fonts.inter,
            fontSize = 13.5.sp,
            lineHeight = 21.sp,
            color = colors.textMuted,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            PrimaryButton(text = action.label, onClick = action.onClick, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
