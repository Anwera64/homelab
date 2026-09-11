package com.homelab.household.app.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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

@Immutable
data class HearthTypography(
    val material: Typography,
    val mono: TextStyle
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

internal fun hearthTypography(fonts: HearthFonts): HearthTypography {
    val base = Typography()
    fun TextStyle.outfit(weight: FontWeight) = copy(fontFamily = fonts.outfit, fontWeight = weight)
    fun TextStyle.inter(weight: FontWeight) = copy(fontFamily = fonts.inter, fontWeight = weight)

    return HearthTypography(
        material = base.copy(
            displayLarge = base.displayLarge.outfit(FontWeight.Bold),
            displayMedium = base.displayMedium.outfit(FontWeight.SemiBold),
            displaySmall = base.displaySmall.outfit(FontWeight.SemiBold),
            headlineLarge = base.headlineLarge.outfit(FontWeight.SemiBold),
            headlineMedium = base.headlineMedium.outfit(FontWeight.SemiBold),
            headlineSmall = base.headlineSmall.outfit(FontWeight.SemiBold),
            titleLarge = base.titleLarge.outfit(FontWeight.Medium),
            titleMedium = base.titleMedium.inter(FontWeight.SemiBold),
            titleSmall = base.titleSmall.inter(FontWeight.Medium),
            bodyLarge = base.bodyLarge.inter(FontWeight.Normal),
            bodyMedium = base.bodyMedium.inter(FontWeight.Normal),
            bodySmall = base.bodySmall.inter(FontWeight.Normal),
            labelLarge = base.labelLarge.inter(FontWeight.SemiBold),
            labelMedium = base.labelMedium.inter(FontWeight.Medium),
            labelSmall = base.labelSmall.inter(FontWeight.Medium)
        ),
        mono = TextStyle(
            fontFamily = fonts.jetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    )
}
