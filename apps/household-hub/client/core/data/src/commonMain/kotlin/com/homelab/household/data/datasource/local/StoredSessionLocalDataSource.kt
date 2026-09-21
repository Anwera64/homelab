package com.homelab.household.data.datasource.local

/**
 * Where this phone keeps the token that says who is signed in, across restarts.
 *
 * One implementation per platform, all with the same semantics: a `null` [refreshToken] leaves the
 * stored one alone, anything unreadable reads as signed out, and none of the four functions throws —
 * a phone that cannot read its own token is a phone nobody is signed in on, not a crash.
 */
interface StoredSessionLocalDataSource {
    fun saveTokens(accessToken: String, refreshToken: String? = null)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clear()
}
