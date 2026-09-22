package com.homelab.household.data.datasource.local

import com.homelab.household.data.dto.UserReadDto

/**
 * What this phone keeps across restarts: the token that says somebody is signed in, and the member
 * the hub last said that token belongs to. The member is kept here rather than in memory because
 * the profile has to draw a name and a colour on a cold start with no hub to ask.
 *
 * One implementation per platform, all with the same semantics: a `null` [refreshToken] leaves the
 * stored one alone, [saveTokens] never disturbs the stored member, [clear] wipes token and member
 * together, anything unreadable reads as signed out, and none of these functions throws — a phone
 * that cannot read its own session is a phone nobody is signed in on, not a crash.
 */
interface StoredSessionLocalDataSource {
    fun saveTokens(accessToken: String, refreshToken: String? = null)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?

    /** Who the kept token belongs to; `null` forgets them and leaves the token alone. */
    fun saveUser(user: UserReadDto?)
    fun getUser(): UserReadDto?
    fun clear()
}
