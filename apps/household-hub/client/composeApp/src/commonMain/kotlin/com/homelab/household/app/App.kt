package com.homelab.household.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.homelab.household.app.navigation.AppNavHost
import com.homelab.household.app.platform.ExternalApps
import com.homelab.household.app.platform.LocalDragHidesKeyboard
import com.homelab.household.app.platform.LocalExternalApps
import com.homelab.household.app.theme.HearthTheme

/**
 * The platform entry point: the theme, the other apps a screen can hand off to, whether scrolling
 * hides the keyboard (iOS, which has no back button for it), and the navigation host.
 */
@Composable
fun App(
    externalApps: ExternalApps,
    dragHidesKeyboard: Boolean = false,
) {
    CompositionLocalProvider(
        LocalExternalApps provides externalApps,
        LocalDragHidesKeyboard provides dragHidesKeyboard,
    ) {
        HearthTheme {
            AppNavHost()
        }
    }
}
