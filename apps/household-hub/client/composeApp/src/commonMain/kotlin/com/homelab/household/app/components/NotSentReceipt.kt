package com.homelab.household.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.conversation_not_sent
import com.homelab.household.app.resources.conversation_retry
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

const val NOT_SENT_GLYPH_TAG = "NotSentGlyph"
const val RETRY_GLYPH_TAG = "RetryGlyph"

/**
 * ⚠ Not sent · ↻ Retry — under a question the hub never got.
 *
 * One quiet line where the Sent receipt would be, rather than a status and a button side by side:
 * the composer already says the hub can't be reached, so this only has to say which message and
 * offer it again. Retry reads as a link but keeps a full touch target — the extra height sits
 * evenly above and below, so the line itself stays the height of its words.
 *
 * The glyphs say nothing to a screen reader; the words already do.
 */
@Composable
fun NotSentReceipt(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val spacing = HearthTheme.spacing

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HearthIconImage(
            icon = HearthIcon.Error,
            contentDescription = null,
            modifier = Modifier.testTag(NOT_SENT_GLYPH_TAG),
            // The set's smallest drawn size; below it the stroke never reaches full ink.
            size = HearthTheme.size.iconSm,
            tint = colors.error,
        )
        Text(
            text = stringResource(Res.string.conversation_not_sent),
            style = type.labelStrong,
            color = colors.error,
        )
        Text(
            text = "·",
            style = type.caption,
            color = colors.textMuted,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Row(
            modifier =
                Modifier
                    .heightIn(min = HearthTheme.size.touchTarget)
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(horizontal = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HearthIconImage(
                icon = HearthIcon.Retry,
                contentDescription = null,
                modifier = Modifier.testTag(RETRY_GLYPH_TAG),
                size = HearthTheme.size.iconSm,
                tint = colors.primary,
            )
            Text(
                text = stringResource(Res.string.conversation_retry),
                style = type.labelStrong,
                color = colors.primary,
            )
        }
    }
}
