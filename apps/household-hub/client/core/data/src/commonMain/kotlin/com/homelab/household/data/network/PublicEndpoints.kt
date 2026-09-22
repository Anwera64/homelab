package com.homelab.household.data.network

/**
 * The calls the hub answers without a token. Everything else carries one, and a 401 to it means the
 * hub no longer accepts that token. Named once, for the bearer plugin and the sign-out alike.
 */
object PublicEndpoints {
    private val paths =
        listOf(
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
