package com.homelab.household.app.theme

import androidx.compose.ui.tooling.preview.Preview

private const val UI_MODE_NIGHT_NO = 0x10
private const val UI_MODE_NIGHT_YES = 0x20

/**
 * Multipreview annotation that renders a Composable in both Light and Dark themes.
 *
 * Make sure the target Composable uses [HearthTheme] with default `darkTheme = isSystemInDarkTheme()`.
 */
@Preview(
    name = "Light Theme",
    group = "Themes",
    uiMode = UI_MODE_NIGHT_NO,
    showBackground = true,
)
@Preview(
    name = "Dark Theme",
    group = "Themes",
    uiMode = UI_MODE_NIGHT_YES,
    showBackground = true,
)
annotation class PreviewDayNight
