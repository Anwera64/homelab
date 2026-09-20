package com.homelab.household.data.datasource.local

class InMemoryTokenStorage : TokenLocalDataSource {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    override fun saveTokens(accessToken: String, refreshToken: String?) {
        this.accessToken = accessToken
        if (refreshToken != null) {
            this.refreshToken = refreshToken
        }
    }

    override fun getAccessToken(): String? = accessToken

    override fun getRefreshToken(): String? = refreshToken

    override fun clear() {
        accessToken = null
        refreshToken = null
    }
}
