package com.homelab.household.data.remote

import com.homelab.household.data.local.TokenStorage
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.http.HttpStatusCode

/**
 * The calls the hub answers without a token. Everything else carries one, and a 401 to it means the
 * hub no longer accepts that token. Named once, for the bearer plugin and the sign-out alike.
 */
object PublicEndpoints {
    private val paths = listOf(
        Regex("/api/v1/auth/(login|register-initial|members|status)"),
        Regex("/api/v1/health"),
        Regex("/api/v1/invites/[^/]+(/redeem)?"),
        Regex("/api/v1/auth/pin-resets/[^/]+/redeem"),
    )

    fun isPublic(url: String): Boolean {
        val path = "/" + url.substringAfter("://").substringAfter('/', "").substringBefore('?')
        return paths.any { it.matches(path) }
    }
}

/**
 * Forgets the token when the hub stops accepting it — a PIN changed on another device, or the member
 * was removed — and tells [onSignedOut] once. A 401 from a public call is sign-in refusing a PIN,
 * and a wrong PIN while signed in comes back as 403, so neither signs anyone out.
 */
fun HttpClientConfig<*>.signOutOnUnauthorized(tokenStorage: TokenStorage, onSignedOut: () -> Unit) {
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
