package com.homelab.household.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.inter_400
import com.homelab.household.app.resources.inter_500
import com.homelab.household.app.resources.inter_600
import com.homelab.household.app.resources.jetbrains_mono_500
import com.homelab.household.app.resources.outfit_500
import com.homelab.household.app.resources.outfit_600
import com.homelab.household.app.resources.outfit_700
import org.jetbrains.compose.resources.Font

/** Outfit for display, Inter for text, JetBrains Mono for times, handles and metadata. */
@Immutable
data class HearthFonts(
    val outfit: FontFamily,
    val inter: FontFamily,
    val jetBrainsMono: FontFamily
)

@Composable
internal fun rememberHearthFonts(): HearthFonts {
    val outfit = FontFamily(
        Font(Res.font.outfit_500, FontWeight.Medium),
        Font(Res.font.outfit_600, FontWeight.SemiBold),
        Font(Res.font.outfit_700, FontWeight.Bold)
    )
    val inter = FontFamily(
        Font(Res.font.inter_400, FontWeight.Normal),
        Font(Res.font.inter_500, FontWeight.Medium),
        Font(Res.font.inter_600, FontWeight.SemiBold)
    )
    val jetBrainsMono = FontFamily(Font(Res.font.jetbrains_mono_500, FontWeight.Medium))
    return remember(outfit, inter, jetBrainsMono) { HearthFonts(outfit, inter, jetBrainsMono) }
}
