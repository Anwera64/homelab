package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.field_changed
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

/**
 * One of a few choices, side by side in a tray (canvas: the which-dates switch, Repeat, Ends). The
 * chosen one sits lifted on the surface. [label] names the group to a screen reader.
 */
@Composable
fun <T> HearthSegmented(
    options: List<Pair<T, String>>,
    chosen: T,
    onChoose: (T) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(HearthShapes.item)
                .background(colors.canvas)
                .border(HearthTheme.size.hairline, colors.outline, HearthShapes.item)
                .padding(HearthTheme.spacing.xs)
                .semantics { contentDescription = label }
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
    ) {
        options.forEach { (option, words) ->
            val selected = option == chosen
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = HearthTheme.size.touchTarget - HearthTheme.spacing.xs)
                        .clip(HearthShapes.button)
                        .background(if (selected) colors.surface else colors.canvas)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onChoose(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = words,
                    style = HearthTheme.typography.label,
                    color = if (selected) colors.textPrimary else colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A field's name over it, said as "… · changed" in the accent once it no longer says what was proposed. */
@Composable
fun HearthFieldLabel(
    label: String,
    modifier: Modifier = Modifier,
    changed: Boolean = false,
) {
    val colors = HearthTheme.colors
    Text(
        modifier = modifier,
        text = if (changed) stringResource(Res.string.field_changed, label) else label,
        style = HearthTheme.typography.labelStrong,
        color = if (changed) colors.primary else colors.textMuted,
    )
}
