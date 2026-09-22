package com.homelab.household

import com.homelab.household.app.platform.ExternalApps
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * Opens other apps on iOS, and the twin of `AndroidExternalApps`.
 *
 * Android can ask the package manager for a launch intent; iOS has no equivalent, so the only way
 * to tell whether Tailscale is installed is to ask whether anything answers its URL scheme. That
 * question is answered honestly only for schemes listed under `LSApplicationQueriesSchemes` in
 * `Info.plist` — without the entry `canOpenURL` returns false even when the app is there. The
 * entry lives in `iosApp/project.yml`, which is the source of truth for the generated plist, and
 * is the counterpart of the `<queries>` block in the Android manifest.
 *
 * Both calls go through `UIApplication.sharedApplication`, which is main-thread only; Compose
 * calls this from the composition, so that already holds.
 */
class IosExternalApps : ExternalApps {
    override fun openTailscale() {
        val application = UIApplication.sharedApplication
        val tailscale = NSURL.URLWithString(TAILSCALE_SCHEME)
        val target =
            if (tailscale != null && application.canOpenURL(tailscale)) {
                tailscale
            } else {
                // Not installed, or the scheme was not declared: fall back to its store page, the same
                // way Android falls back to the Play listing.
                NSURL.URLWithString(TAILSCALE_STORE_URL)
            } ?: return

        application.openURL(target, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    private companion object {
        const val TAILSCALE_SCHEME = "tailscale://"
        const val TAILSCALE_STORE_URL = "https://apps.apple.com/app/tailscale/id1470499037"
    }
}
