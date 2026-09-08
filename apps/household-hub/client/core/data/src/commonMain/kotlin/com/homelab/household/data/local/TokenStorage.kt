package com.homelab.household.data.local

interface TokenStorage {
    fun saveTokens(accessToken: String, refreshToken: String? = null)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clear()
}
