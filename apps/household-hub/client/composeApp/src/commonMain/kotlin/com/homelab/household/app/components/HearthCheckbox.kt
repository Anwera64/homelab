package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/**
 * A box to tick, drawn on its own: filled green with a tick when [checked], an outline when not.
 * It takes no taps itself; [HearthCheckboxRow] makes the whole row one target.
 */
@Composable
fun HearthCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val box =
        if (checked) {
            Modifier.background(colors.primary, HearthShapes.checkbox)
        } else {
            Modifier.border(HearthTheme.size.emphasis, colors.outline, HearthShapes.checkbox)
        }
    Box(
        modifier = modifier.size(HearthTheme.size.iconMd).then(box),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            HearthIconImage(
                icon = HearthIcon.Sent,
                contentDescription = null,
                size = HearthTheme.size.iconSm,
                tint = colors.onPrimary,
            )
        }
    }
}

/**
 * A choice inside a card, like "Auto-approve adding events from now on" (canvas: ToolAutoApprove).
 * The whole row is the target, and its border turns green once it is ticked, so a ticked row reads
 * as a decision made before the button that acts on it is tapped.
 */
@Composable
fun HearthCheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = HearthTheme.size.touchTarget)
                .clip(HearthShapes.item)
                .border(HearthTheme.size.hairline, if (checked) colors.primary else colors.outline, HearthShapes.item)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(horizontal = HearthTheme.spacing.md, vertical = HearthTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HearthCheckbox(checked = checked)
        Text(text = text, style = HearthTheme.typography.label, color = colors.textPrimary)
    }
}
