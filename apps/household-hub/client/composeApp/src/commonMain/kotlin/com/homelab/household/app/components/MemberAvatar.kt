package com.homelab.household.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.app.util.hexColor

/**
 * A member's face: their initial on their own colour. The glyph is sized by the circle, one step
 * per diameter (design notes §3), so the caller passes both. Decorative: the name beside it is
 * what a screen reader reads.
 */
@Composable
fun MemberAvatar(
    name: String,
    colour: String,
    size: Dp,
    glyph: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    Box(
        modifier =
            modifier
                .size(size)
                .background(hexColor(colour, fallback = colors.primary), CircleShape)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text =
                name
                    .trim()
                    .firstOrNull()
                    ?.uppercase()
                    .orEmpty(),
            style = glyph,
            color = colors.onPrimary,
        )
    }
}
