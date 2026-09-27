package com.homelab.household

import com.homelab.household.app.platform.ExternalApps
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

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
 * A browser sign-in runs in `ASWebAuthenticationSession`, which catches the hub's
 * `hyggehub://calendar/…` redirect itself, so no URL type needs registering for it.
 *
 * Every call goes through UIKit, which is main-thread only; Compose calls this from the
 * composition and its effects, so that already holds.
 */
class IosExternalApps : ExternalApps {
    /** The session's context provider is a weak reference, so this keeps it alive. */
    private val anchor = KeyWindowAnchor()

    /** And the session itself must be held while it is on screen. */
    private var session: ASWebAuthenticationSession? = null

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

    override suspend fun signInInBrowser(
        page: String,
        callbackScheme: String,
    ): String? {
        val url = NSURL.URLWithString(page) ?: return null
        return suspendCancellableCoroutine { continuation ->
            val signIn =
                ASWebAuthenticationSession(uRL = url, callbackURLScheme = callbackScheme) { returnedTo, _ ->
                    session = null
                    // An error here is the member cancelling; either way there is no address to read.
                    if (continuation.isActive) continuation.resume(returnedTo?.absoluteString)
                }
            signIn.presentationContextProvider = anchor
            // Share Safari's cookies, so a member already signed in to Google only has to say yes.
            signIn.prefersEphemeralWebBrowserSession = false
            session = signIn
            continuation.invokeOnCancellation { signIn.cancel() }
            if (!signIn.start()) {
                session = null
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private companion object {
        const val TAILSCALE_SCHEME = "tailscale://"
        const val TAILSCALE_STORE_URL = "https://apps.apple.com/app/tailscale/id1470499037"
    }
}

/** Shows the sign-in over the app's own window. */
private class KeyWindowAnchor :
    NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor = UIApplication.sharedApplication.keyWindow ?: UIWindow()
}
