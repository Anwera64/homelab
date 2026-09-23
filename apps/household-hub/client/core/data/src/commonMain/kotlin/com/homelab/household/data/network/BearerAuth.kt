package com.homelab.household.data.network

import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer

/**
 * Puts this phone's kept token on every call that is not public — see [PublicEndpoints].
 *
 * The token is read from [storage] for every call rather than cached by Ktor. Ktor's default keeps
 * the first token it loads until told to forget it, so after one member signed out and another
 * signed in, the calls went on carrying the first member's token — and did what that member asked.
 * Signing in, renewing, changing a PIN and signing out all write to [storage]; reading it each time
 * means none of them has to remember to tell the client as well.
 */
fun HttpClientConfig<*>.installBearerAuth(storage: StoredSessionLocalDataSource) {
    install(Auth) {
        bearer {
            cacheTokens = false
            loadTokens {
                val access = storage.getAccessToken()
                val refresh = storage.getRefreshToken()
                if (access != null) {
                    BearerTokens(accessToken = access, refreshToken = refresh ?: "")
                } else {
                    null
                }
            }
            sendWithoutRequest { request -> !PublicEndpoints.isPublic(request.url.buildString()) }
        }
    }
}
