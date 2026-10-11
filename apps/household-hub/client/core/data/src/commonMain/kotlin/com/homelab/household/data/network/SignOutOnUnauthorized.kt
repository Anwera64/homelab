package com.homelab.household.data.network

import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.http.HttpStatusCode

/** Where a phone tells the hub it is signing out. */
const val SIGN_OUT_PATH = "/api/v1/auth/logout"

/**
 * Forgets the token when the hub stops accepting it — a PIN changed on another device, or the member
 * was removed — and tells [onSignedOut] once. A 401 from a public call is sign-in refusing a PIN,
 * and a wrong PIN while signed in comes back as 403, so neither signs anyone out.
 *
 * Nor does a 401 to the member's own sign-out: they asked to be signed out, the hub had only got
 * there first, and whoever sent the call forgets the token whatever the answer.
 */
fun HttpClientConfig<*>.signOutOnUnauthorized(
    storage: StoredSessionLocalDataSource,
    onSignedOut: () -> Unit,
) {
    HttpResponseValidator {
        validateResponse { response ->
            val url =
                response.call.request.url
                    .toString()
            if (response.status == HttpStatusCode.Unauthorized &&
                !PublicEndpoints.isPublic(url) &&
                !url.substringBefore('?').endsWith(SIGN_OUT_PATH) &&
                storage.getAccessToken() != null
            ) {
                storage.clear()
                onSignedOut()
            }
        }
    }
}
