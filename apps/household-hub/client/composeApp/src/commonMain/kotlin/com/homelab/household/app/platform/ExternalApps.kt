package com.homelab.household.app.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The other apps a screen can hand the user to. Each platform supplies its own through [App];
 * tests supply a fake that counts.
 */
interface ExternalApps {
    /** Tailscale, which is how the hub is reached away from home — or its store page. */
    fun openTailscale()

    /**
     * Opens [page] in the platform's sign-in browser (Custom Tabs, `ASWebAuthenticationSession`) and
     * waits for it to come back. Returns the address the browser was sent to when it uses
     * [callbackScheme], or null when the member closed the browser first.
     */
    suspend fun signInInBrowser(
        page: String,
        callbackScheme: String,
    ): String?
}

val LocalExternalApps =
    staticCompositionLocalOf<ExternalApps> {
        error("No ExternalApps provided: App() and TestApp provide one")
    }
