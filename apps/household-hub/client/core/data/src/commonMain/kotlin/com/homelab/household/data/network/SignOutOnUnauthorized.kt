package com.homelab.household.data.network

import com.homelab.household.data.datasource.local.TokenLocalDataSource
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.http.HttpStatusCode

/**
 * Forgets the token when the hub stops accepting it — a PIN changed on another device, or the member
 * was removed — and tells [onSignedOut] once. A 401 from a public call is sign-in refusing a PIN,
 * and a wrong PIN while signed in comes back as 403, so neither signs anyone out.
 */
fun HttpClientConfig<*>.signOutOnUnauthorized(tokenStorage: TokenLocalDataSource, onSignedOut: () -> Unit) {
    HttpResponseValidator {
        validateResponse { response ->
            if (response.status == HttpStatusCode.Unauthorized &&
                !PublicEndpoints.isPublic(response.call.request.url.toString()) &&
                tokenStorage.getAccessToken() != null
            ) {
                tokenStorage.clear()
                onSignedOut()
            }
        }
    }
}
