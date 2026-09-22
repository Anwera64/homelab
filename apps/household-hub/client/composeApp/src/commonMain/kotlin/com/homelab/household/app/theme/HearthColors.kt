package com.homelab.household.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The Hearth token set. Values come from the palette proof (design notes §3).
 *
 * Ghost alphas are per palette on purpose: one shared value reads ~1.5× weaker in daylight.
 */
@Immutable
data class HearthColors(
    val canvas: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surface3: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val secret: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val outline: Color,
    val outlineSoft: Color,
    val ghostTint: Float,
    val ghostEdge: Float,
    val ghostFrame: Float,
)

/** Copenhagen Day. */
val DayColors =
    HearthColors(
        canvas = Color(0xFFF5F2EB),
        surface = Color(0xFFFFFFFF),
        surfaceAlt = Color(0xFFFAF8F3),
        surface3 = Color(0xFFFFFFFF),
        primary = Color(0xFF3C6E4E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD0DFD5),
        onPrimaryContainer = Color(0xFF102318),
        secondary = Color(0xFFC05638),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD5C7),
        onSecondaryContainer = Color(0xFF541A0B),
        error = Color(0xFFA33B2A),
        errorContainer = Color(0xFFFBDDD6),
        onErrorContainer = Color(0xFF5C1508),
        success = Color(0xFF3A7455),
        successContainer = Color(0xFFD2E7DA),
        onSuccessContainer = Color(0xFF0F2E1F),
        secret = Color(0xFF6B655F),
        textPrimary = Color(0xFF221F1E),
        textMuted = Color(0xFF514B47),
        outline = Color(0xFFDED7CA),
        outlineSoft = Color(0xFFEFEBE4),
        ghostTint = 0.20f,
        ghostEdge = 0.69f,
        ghostFrame = 0.66f,
    )

/** Midnight Espresso. */
val NightColors =
    HearthColors(
        canvas = Color(0xFF100F0E),
        surface = Color(0xFF191715),
        surfaceAlt = Color(0xFF211E1B),
        surface3 = Color(0xFF2A2622),
        primary = Color(0xFF7FB894),
        onPrimary = Color(0xFF100F0E),
        primaryContainer = Color(0xFF26352D),
        onPrimaryContainer = Color(0xFF9BC2AC),
        secondary = Color(0xFFE5855E),
        onSecondary = Color(0xFF100F0E),
        secondaryContainer = Color(0xFF381E15),
        onSecondaryContainer = Color(0xFFFFB091),
        error = Color(0xFFE8705C),
        errorContainer = Color(0xFF4A160C),
        onErrorContainer = Color(0xFFFFB4A4),
        success = Color(0xFF6FB58E),
        successContainer = Color(0xFF16342A),
        onSuccessContainer = Color(0xFFA5D6BC),
        secret = Color(0xFFB8B2AC),
        textPrimary = Color(0xFFE8E2DC),
        textMuted = Color(0xFF9C948D),
        outline = Color(0xFF282420),
        outlineSoft = Color(0xFF272523),
        ghostTint = 0.14f,
        ghostEdge = 0.55f,
        ghostFrame = 0.45f,
    )
