package com.homelab.household

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.homelab.household.app.platform.ExternalApps

/** Opens other apps on Android. Tailscale's package is declared under `<queries>` so it can be seen. */
class AndroidExternalApps(private val context: Context) : ExternalApps {

    override fun openTailscale() {
        val intent = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$TAILSCALE_PACKAGE"))
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Neither Tailscale nor anything that opens a store link: nothing to hand over to.
        }
    }

    private companion object {
        const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    }
}
