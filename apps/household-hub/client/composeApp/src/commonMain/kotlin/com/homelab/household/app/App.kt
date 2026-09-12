package com.homelab.household.app

import androidx.compose.runtime.Composable
import com.homelab.household.app.navigation.AppNavHost
import com.homelab.household.app.theme.HearthTheme

/** The platform entry point: the theme, and the navigation host under it. */
@Composable
fun App() {
    HearthTheme {
        AppNavHost()
    }
}
