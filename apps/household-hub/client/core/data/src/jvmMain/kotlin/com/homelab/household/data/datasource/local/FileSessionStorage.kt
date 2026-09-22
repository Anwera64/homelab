package com.homelab.household.data.datasource.local

import com.homelab.household.data.dto.UserReadDto
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileSessionStorage(
    storageDir: String? = null
) : StoredSessionLocalDataSource {

    private val json = Json { ignoreUnknownKeys = true }
    private val tokenFile: File
    private var inMemoryAccessToken: String? = null
    private var inMemoryRefreshToken: String? = null
    private var inMemoryUser: UserReadDto? = null

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
                    val payload = json.decodeFromString<SessionDiskPayload>(text)
                    inMemoryAccessToken = payload.accessToken
                    inMemoryRefreshToken = payload.refreshToken
                    inMemoryUser = payload.user
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
        write()
    }

    override fun getAccessToken(): String? = inMemoryAccessToken

    override fun getRefreshToken(): String? = inMemoryRefreshToken

    override fun saveUser(user: UserReadDto?) {
        inMemoryUser = user
        write()
    }

    override fun getUser(): UserReadDto? = inMemoryUser

    override fun clear() {
        inMemoryAccessToken = null
        inMemoryRefreshToken = null
        inMemoryUser = null
        try {
            if (tokenFile.exists()) {
                tokenFile.delete()
            }
        } catch (_: Exception) {
        }
    }

    /** Writes everything this machine currently knows, so no field can be dropped by a partial save. */
    private fun write() {
        try {
            val payload = SessionDiskPayload(
                accessToken = inMemoryAccessToken,
                refreshToken = inMemoryRefreshToken,
                user = inMemoryUser
            )
            tokenFile.writeText(json.encodeToString(payload))
        } catch (_: Exception) {
        }
    }
}
