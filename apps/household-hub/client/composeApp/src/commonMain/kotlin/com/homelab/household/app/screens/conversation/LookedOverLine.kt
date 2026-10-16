package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.homelab.household.app.components.ChipVariant
import com.homelab.household.app.components.HearthChip
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.chat_looked_over
import com.homelab.household.app.resources.chat_looked_over_nothing
import com.homelab.household.app.resources.chat_looked_over_remembered
import com.homelab.household.app.resources.profile_memories_to_check
import com.homelab.household.app.screens.changes.words
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.domain.model.LookedOverLine
import com.homelab.household.presentation.memories.LocalTime
import org.jetbrains.compose.resources.stringResource

/** Tags the quiet line so a test can tap it and find where it sits. */
internal const val LOOKED_OVER_LINE_TAG = "looked-over-line"

/**
 * The quiet line after the last message a look-over read (canvas MemChatLine): "Remembered 2", a
 * "1 to check" chip and the time, between two hairlines; a tap opens "Remembered from this chat".
 * When nothing was kept it reads "Looked over · 10:52 · nothing new" and opens nothing
 * (MemChatLineNothing).
 */
@Composable
internal fun LookedOverLine(
    line: LookedOverLine,
    at: LocalTime,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val time = at.words()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HearthTheme.size.touchTarget)
            .clip(HearthShapes.item)
            .then(
                if (line.isNothingNew) Modifier else Modifier.clickable(role = Role.Button, onClick = onOpen),
            ).testTag(LOOKED_OVER_LINE_TAG),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.outlineSoft)
        if (line.isNothingNew) {
            Text(
                stringResource(Res.string.chat_looked_over_nothing, time),
                style = type.caption,
                color = colors.textMuted,
            )
        } else {
            Text(
                if (line.remembered > 0) {
                    stringResource(Res.string.chat_looked_over_remembered, line.remembered)
                } else {
                    stringResource(Res.string.chat_looked_over)
                },
                style = type.caption,
                color = colors.textMuted,
            )
            if (line.toCheck > 0) {
                HearthChip(
                    label = stringResource(Res.string.profile_memories_to_check, line.toCheck),
                    variant = ChipVariant.Secondary,
                )
            }
            Text(time, style = type.monoSm, color = colors.textMuted)
            HearthIconImage(
                icon = HearthIcon.ChevronRight,
                contentDescription = null,
                size = HearthTheme.size.iconSm,
                tint = colors.textMuted,
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.outlineSoft)
    }
}
