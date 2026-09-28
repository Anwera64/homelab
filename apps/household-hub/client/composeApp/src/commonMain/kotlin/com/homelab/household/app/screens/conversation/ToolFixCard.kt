package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.homelab.household.app.components.ActionCard
import com.homelab.household.app.components.CardButtonRow
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_fix_ask_again
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

/**
 * A step that failed in a way the member can fix (canvas: ToolFailed): a red icon, what broke, that
 * nothing was written, and one button. The step's red line isn't repeated above it; it stays in
 * the fold.
 *
 * Once it is [ToolFix.fixed] (canvas: ToolFixedCard) the card turns neutral and says to ask again.
 * [onAskAgain] adds the retry icon that does it, and is only given while this is the chat's latest
 * answer: after a newer message the question has moved on (canvas: ToolFixedLater).
 */
@Composable
fun ToolFixCard(
    fix: ToolFix,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
    onAskAgain: (() -> Unit)? = null,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val detail = stringResource(fix.detail)
    val caption = fix.outcome?.let { "$detail ${stringResource(it)}" } ?: detail
    val action = fix.action

    ActionCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
            verticalAlignment = if (onAskAgain != null) Alignment.CenterVertically else Alignment.Top,
        ) {
            // Nudged down to sit on the title's first line, as the canvas draws it.
            HearthIconImage(
                icon = fix.icon,
                contentDescription = null,
                size = HearthTheme.size.iconSm,
                tint = if (fix.fixed) colors.primary else colors.error,
                modifier = Modifier.padding(top = HearthTheme.spacing.xxs),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
            ) {
                Text(text = stringResource(fix.title), style = type.bodyStrong, color = colors.textPrimary)
                Text(text = caption, style = type.caption, color = colors.textMuted)
            }
            if (onAskAgain != null) {
                IconButton(onClick = onAskAgain, modifier = Modifier.size(HearthTheme.size.touchTarget)) {
                    HearthIconImage(
                        icon = HearthIcon.Retry,
                        contentDescription = stringResource(Res.string.tool_fix_ask_again),
                        size = HearthTheme.size.iconMd,
                        tint = colors.primary,
                    )
                }
            }
        }
        if (action != null) {
            CardButtonRow {
                PrimaryButton(
                    text = stringResource(action),
                    onClick = onFix,
                    modifier = Modifier.weight(1f),
                    lifted = false,
                )
            }
        }
    }
}
