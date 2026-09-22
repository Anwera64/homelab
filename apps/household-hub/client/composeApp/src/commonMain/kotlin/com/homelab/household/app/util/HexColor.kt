package com.homelab.household.app.util

import androidx.compose.ui.graphics.Color

/**
 * A member's colour as the hub stores it, `#RRGGBB`. It comes from data, not the theme, so
 * anything malformed falls back rather than crashing a screen.
 */
fun hexColor(
    hex: String,
    fallback: Color,
): Color {
    val digits = hex.removePrefix("#")
    if (digits.length != 6) return fallback
    val rgb = digits.toLongOrNull(16) ?: return fallback
    return Color(0xFF000000 or rgb)
}
