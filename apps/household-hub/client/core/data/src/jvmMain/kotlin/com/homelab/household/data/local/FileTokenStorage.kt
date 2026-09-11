package com.homelab.household.data.local

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileTokenStorage(
    storageDir: String? = null
) : TokenStorage {

    private val json = Json { ignoreUnknownKeys = true }
    private val tokenFile: File
    private var inMemoryAccessToken: String? = null
    private var inMemoryRefreshToken: String? = null

    init {
        val baseDir = if (!storageDir.isNullOrBlank()) {
            File(storageDir)
        } else {
            val userHome = System.getProperty("user.home") ?: "."
            File(userHome, ".household_hub")
        }
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        tokenFile = File(baseDir, "auth_tokens.json")
        loadFromDisk()
    }

    private fun loadFromDisk() {
        try {
            if (tokenFile.exists()) {
                val text = tokenFile.readText()
                if (text.isNotBlank()) {
                    val payload = json.decodeFromString<TokenDiskPayload>(text)
                    inMemoryAccessToken = payload.accessToken
                    inMemoryRefreshToken = payload.refreshToken
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun saveTokens(accessToken: String, refreshToken: String?) {
        inMemoryAccessToken = accessToken
        if (refreshToken != null) {
            inMemoryRefreshToken = refreshToken
        }
        try {
            val payload = TokenDiskPayload(
                accessToken = inMemoryAccessToken ?: accessToken,
                refreshToken = inMemoryRefreshToken
            )
            tokenFile.writeText(json.encodeToString(payload))
        } catch (_: Exception) {
        }
    }

    override fun getAccessToken(): String? = inMemoryAccessToken

    override fun getRefreshToken(): String? = inMemoryRefreshToken

    override fun clear() {
        inMemoryAccessToken = null
        inMemoryRefreshToken = null
        try {
            if (tokenFile.exists()) {
                tokenFile.delete()
            }
        } catch (_: Exception) {
        }
    }
}
