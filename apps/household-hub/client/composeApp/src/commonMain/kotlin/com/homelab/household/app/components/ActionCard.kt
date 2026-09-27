package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme

/**
 * Anything on the agent's side that needs you or is still working (canvas: tools-states): an
 * approval card, a fix to make, a tool running. White surface, 16 px corners and a soft shadow;
 * everything else in an answer sits straight on the page.
 */
@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .actionCardSurface()
                .padding(HearthTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md),
        content = content,
    )
}

/** The card's surface on its own, for [ToolRunningChip], which is one that hugs its words. */
@Composable
fun Modifier.actionCardSurface(): Modifier {
    val colors = HearthTheme.colors
    return shadow(
        elevation = HearthTheme.size.raised,
        shape = HearthShapes.item,
        ambientColor = colors.textPrimary.copy(alpha = CARD_SHADOW_ALPHA),
        spotColor = colors.textPrimary.copy(alpha = CARD_SHADOW_ALPHA),
    ).background(colors.surface, HearthShapes.item)
}

/**
 * A card's buttons: they share its full width, the main action last, on the right. The buttons
 * inside drop their own shadow, since the card already lifts them (canvas: tools-slice-todo).
 * Each is given its share with `Modifier.weight(1f)` from [content].
 */
@Composable
fun CardButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
        content = content,
    )
}

private const val CARD_SHADOW_ALPHA = 0.06f
