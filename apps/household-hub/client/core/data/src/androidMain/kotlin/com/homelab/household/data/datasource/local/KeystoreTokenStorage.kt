package com.homelab.household.data.datasource.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json

/**
 * Keeps auth tokens encrypted with an AES-256-GCM key held in the Android Keystore.
 *
 * The file lives in `noBackupFilesDir`: Keystore keys never leave the device, so a restored
 * backup could not be decrypted anyway. Anything unreadable is deleted and reads as signed out.
 */
class KeystoreTokenStorage(context: Context) : TokenLocalDataSource {

    private val tokenFile = File(context.noBackupFilesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }

    private var loaded = false
    private var accessToken: String? = null
    private var refreshToken: String? = null

    @Synchronized
    override fun saveTokens(accessToken: String, refreshToken: String?) {
        ensureLoaded()
        this.accessToken = accessToken
        if (refreshToken != null) {
            this.refreshToken = refreshToken
        }
        try {
            write(TokenDiskPayload(accessToken = accessToken, refreshToken = this.refreshToken))
        } catch (_: Exception) {
        }
    }

    @Synchronized
    override fun getAccessToken(): String? {
        ensureLoaded()
        return accessToken
    }

    @Synchronized
    override fun getRefreshToken(): String? {
        ensureLoaded()
        return refreshToken
    }

    @Synchronized
    override fun clear() {
        loaded = true
        accessToken = null
        refreshToken = null
        tokenFile.delete()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!tokenFile.exists()) return
        try {
            val bytes = tokenFile.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_LENGTH))
            val plain = cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH)
            val payload = json.decodeFromString<TokenDiskPayload>(plain.decodeToString())
            accessToken = payload.accessToken
            refreshToken = payload.refreshToken
        } catch (_: Exception) {
            tokenFile.delete()
        }
    }

    private fun write(payload: TokenDiskPayload) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json.encodeToString(payload).encodeToByteArray())
        tokenFile.parentFile?.mkdirs()
        tokenFile.writeBytes(cipher.iv + encrypted)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val FILE_NAME = "auth_tokens.bin"
        const val KEY_ALIAS = "household_hub_tokens"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
