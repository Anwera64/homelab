package com.homelab.household.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.conversation_sent
import com.homelab.household.app.theme.HearthTheme
import org.jetbrains.compose.resources.stringResource

const val SENT_GLYPH_TAG = "SentGlyph"

/**
 * ✓ Sent — under the newest question, once the hub has it.
 *
 * Glyph and word take the same muted token, so both follow the theme. The glyph says nothing to a
 * screen reader: the word already does, and hearing it twice is noise.
 */
@Composable
fun SentReceipt(modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HearthIconImage(
            icon = HearthIcon.Sent,
            contentDescription = null,
            modifier = Modifier.testTag(SENT_GLYPH_TAG),
            // The set's smallest drawn size. Below it the 1.5 stroke is thinner than a pixel and
            // never reaches full ink: at 12 dp it measured 2.7:1 by day, under the 3:1 floor.
            size = HearthTheme.size.iconSm,
            tint = colors.textMuted,
        )
        Text(
            text = stringResource(Res.string.conversation_sent),
            style = HearthTheme.typography.monoSm,
            color = colors.textMuted,
        )
    }
}
