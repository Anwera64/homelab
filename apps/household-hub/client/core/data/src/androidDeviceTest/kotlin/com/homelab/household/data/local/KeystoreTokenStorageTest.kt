package com.homelab.household.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreTokenStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tokenFile get() = File(context.noBackupFilesDir, "auth_tokens.bin")

    @Before
    fun setUp() {
        KeystoreTokenStorage(context).clear()
    }

    @Test
    fun saved_tokens_survive_a_new_instance() {
        KeystoreTokenStorage(context).saveTokens(accessToken = "access-123", refreshToken = "refresh-456")

        val restarted = KeystoreTokenStorage(context)
        assertEquals("access-123", restarted.getAccessToken())
        assertEquals("refresh-456", restarted.getRefreshToken())
    }

    @Test
    fun clear_signs_out_for_new_instances() {
        val storage = KeystoreTokenStorage(context)
        storage.saveTokens(accessToken = "access-123", refreshToken = "refresh-456")
        storage.clear()

        val restarted = KeystoreTokenStorage(context)
        assertNull(restarted.getAccessToken())
        assertNull(restarted.getRefreshToken())
        assertFalse(tokenFile.exists())
    }

    @Test
    fun token_file_is_encrypted_and_excluded_from_backup() {
        KeystoreTokenStorage(context).saveTokens(accessToken = "access-123", refreshToken = "refresh-456")

        assertTrue("Token file should live in noBackupFilesDir", tokenFile.exists())
        val onDisk = tokenFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("Access token must not be stored in plain text", onDisk.contains("access-123"))
        assertFalse("Refresh token must not be stored in plain text", onDisk.contains("refresh-456"))
    }

    @Test
    fun corrupted_file_reads_as_signed_out_and_is_deleted() {
        tokenFile.parentFile?.mkdirs()
        tokenFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17))

        val storage = KeystoreTokenStorage(context)
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertFalse(tokenFile.exists())
    }
}
