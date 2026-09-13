package com.homelab.household.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.homelab.household.app.navigation.AppNavHost
import com.homelab.household.app.platform.ExternalApps
import com.homelab.household.app.platform.LocalExternalApps
import com.homelab.household.app.theme.HearthTheme

/** The platform entry point: the theme, the other apps a screen can hand off to, and the navigation host. */
@Composable
fun App(externalApps: ExternalApps) {
    CompositionLocalProvider(LocalExternalApps provides externalApps) {
        HearthTheme {
            AppNavHost()
        }
    }
}
