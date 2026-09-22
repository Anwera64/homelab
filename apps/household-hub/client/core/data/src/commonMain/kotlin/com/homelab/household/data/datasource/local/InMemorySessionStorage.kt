package com.homelab.household.data.datasource.local

import com.homelab.household.data.dto.UserReadDto

class InMemorySessionStorage : StoredSessionLocalDataSource {
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var user: UserReadDto? = null

    override fun saveTokens(
        accessToken: String,
        refreshToken: String?,
    ) {
        this.accessToken = accessToken
        if (refreshToken != null) {
            this.refreshToken = refreshToken
        }
    }

    override fun getAccessToken(): String? = accessToken

    override fun getRefreshToken(): String? = refreshToken

    override fun saveUser(user: UserReadDto?) {
        this.user = user
    }

    override fun getUser(): UserReadDto? = user

    override fun clear() {
        accessToken = null
        refreshToken = null
        user = null
    }
}
