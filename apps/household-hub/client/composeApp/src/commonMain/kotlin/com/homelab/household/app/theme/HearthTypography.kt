package com.homelab.household.app.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class HearthTypography(
    val material: Typography,
    val mono: TextStyle
)

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
