package com.homelab.household.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalHearthColors = staticCompositionLocalOf { DayColors }
private val LocalHearthFonts = staticCompositionLocalOf<HearthFonts> { error("HearthTheme is not applied") }
private val LocalHearthTypography = staticCompositionLocalOf<HearthTypography> { error("HearthTheme is not applied") }

// Space and size are the same in both palettes, but they still come through the theme: a later
// size class provides its own scale here and every screen under it follows, untouched.
internal val LocalHearthSpacing = staticCompositionLocalOf { DefaultSpacing }
internal val LocalHearthSizes = staticCompositionLocalOf { DefaultSizes }

/** Follows the system theme unless told otherwise. Secret Mode never changes the theme. */
@Composable
fun HearthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) NightColors else DayColors
    val fonts = rememberHearthFonts()
    val typography = remember(fonts) { hearthTypography(fonts) }

    CompositionLocalProvider(
        LocalHearthColors provides colors,
        LocalHearthFonts provides fonts,
        LocalHearthTypography provides typography,
        LocalHearthSpacing provides DefaultSpacing,
        LocalHearthSizes provides DefaultSizes
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            typography = typography.material,
            shapes = HearthShapes.material,
            content = content
        )
    }
}

object HearthTheme {
    val colors: HearthColors
        @Composable
        @ReadOnlyComposable
        get() = LocalHearthColors.current

    val fonts: HearthFonts
        @Composable
        @ReadOnlyComposable
        get() = LocalHearthFonts.current

    val typography: HearthTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalHearthTypography.current

    val spacing: HearthSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalHearthSpacing.current

    val size: HearthSizes
        @Composable
        @ReadOnlyComposable
        get() = LocalHearthSizes.current
}

private fun HearthColors.toColorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        error = error,
        onError = onPrimary,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = canvas,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = textMuted,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surface3,
        outline = outline,
        outlineVariant = outlineSoft
    )
}
