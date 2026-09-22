package com.homelab.household.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.homelab.household.app.theme.DayNightPreviews
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.firstrun.AvatarPalette

private val SampleNames = listOf("Emma", "Anton", "Lucia", "Marc", "Nora")

/** The size "Who's here?" draws, once per palette colour. */
@DayNightPreviews
@Composable
private fun MemberAvatarHeroPreview() {
    ComponentPreview {
        AvatarRow(size = HearthTheme.size.avatarHero, glyph = HearthTheme.typography.glyphHero)
    }
}

/** The size the PIN pad draws, once per palette colour. */
@DayNightPreviews
@Composable
private fun MemberAvatarTilePreview() {
    ComponentPreview {
        AvatarRow(size = HearthTheme.size.tile, glyph = HearthTheme.typography.glyphXxl)
    }
}

@Composable
private fun AvatarRow(
    size: Dp,
    glyph: TextStyle,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.md)) {
        SampleNames.zip(AvatarPalette.swatches).forEach { (name, colour) ->
            MemberAvatar(name = name, colour = colour, size = size, glyph = glyph)
        }
    }
}
