package com.homelab.household

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.homelab.household.app.platform.ExternalApps
import kotlinx.coroutines.CompletableDeferred

/**
 * Opens other apps on Android. Tailscale's package is declared under `<queries>` so it can be seen.
 *
 * A browser sign-in opens in a Custom Tab. The hub sends the tab back to `hyggehub://calendar/…`,
 * which the manifest routes to [MainActivity]; being `singleTask`, the activity comes back to the
 * front, the tab is closed above it, and [onRedirect] hands the address over. Coming back any other
 * way (the member closed the tab) resumes the activity with nothing handed over: [onResumed] reads
 * that as the sign-in cancelled.
 */
class AndroidExternalApps(
    private val activity: Activity,
) : ExternalApps {
    private var pending: CompletableDeferred<String?>? = null

    override fun openTailscale() {
        val intent =
            activity.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
                ?: Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$TAILSCALE_PACKAGE"),
                )
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Neither Tailscale nor anything that opens a store link: nothing to hand over to.
        }
    }

    override suspend fun signInInBrowser(
        page: String,
        callbackScheme: String,
    ): String? {
        // One sign-in at a time; an older one still waiting is over.
        pending?.complete(null)
        val result = CompletableDeferred<String?>()
        pending = result
        try {
            CustomTabsIntent.Builder().build().launchUrl(activity, Uri.parse(page))
        } catch (_: ActivityNotFoundException) {
            // No browser at all: nothing to sign in with.
            result.complete(null)
        }
        return try {
            result.await()
        } finally {
            if (pending === result) pending = null
        }
    }

    /** The browser came back to [address]. True when a sign-in was waiting for it. */
    fun onRedirect(address: String): Boolean = pending?.complete(address) ?: false

    /** Back in the app. A sign-in still waiting was left by closing the browser. */
    fun onResumed() {
        pending?.complete(null)
    }

    private companion object {
        const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    }
}
