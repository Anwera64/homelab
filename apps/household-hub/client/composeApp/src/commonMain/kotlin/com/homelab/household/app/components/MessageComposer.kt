package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.send_message
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The message box under a conversation. Stateless: sending never clears the text here —
 * the screen decides when a message has really gone. Send is never disabled.
 */
@Composable
fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    val colors = HearthTheme.colors
    val textStyle = TextStyle(fontFamily = HearthTheme.fonts.inter, fontSize = 14.sp, lineHeight = 20.sp)

    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(thickness = HearthTheme.size.hairline, color = colors.outlineSoft)
        Row(
            modifier = Modifier.padding(
                start = HearthTheme.spacing.lg,
                top = HearthTheme.spacing.md,
                end = HearthTheme.spacing.lg,
                bottom = HearthTheme.spacing.lg
            ),
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.invoke()
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = textStyle.copy(color = colors.textPrimary),
                maxLines = 5,
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { innerField ->
                    Box(
                        modifier = Modifier
                            .background(colors.canvas, HearthShapes.pill)
                            .border(HearthTheme.size.hairline, colors.outline, HearthShapes.pill)
                            .padding(horizontal = HearthTheme.spacing.lg, vertical = HearthTheme.spacing.md)
                    ) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = textStyle, color = colors.textMuted)
                        }
                        innerField()
                    }
                }
            )
            Box(
                modifier = Modifier
                    .size(HearthTheme.size.touchTarget)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(role = Role.Button) { onSend(value) },
                contentAlignment = Alignment.Center
            ) {
                HearthIconImage(
                    icon = HearthIcon.Send,
                    contentDescription = stringResource(Res.string.send_message),
                    active = true,
                    size = HearthTheme.size.iconMd,
                    tint = colors.onPrimary
                )
            }
        }
    }
}
