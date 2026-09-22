package com.homelab.household.data.datasource.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homelab.household.data.dto.UserReadDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Android [StoredSessionLocalDataSource], on a real device: the Keystore only exists there.
 *
 * Names here use underscores rather than the backticked `GIVEN � WHEN � THEN �` the rest of
 * `:core:data` uses. A method name containing a space is not legal dex below minSdkVersion 30,
 * and this module is minSdk 26, so D8 would refuse to build this file at all.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSessionStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tokenFile get() = File(context.noBackupFilesDir, "auth_tokens.bin")

    private val emma =
        UserReadDto(
            id = "emma",
            full_name = "Emma Larsson",
            is_admin = true,
            is_active = true,
            avatar_color = "#3C6E4E",
        )

    @Before
    fun signedOutToStart() {
        KeystoreSessionStorage(context).clear()
    }

    @Test
    fun GIVEN_a_token_saved_on_this_phone_WHEN_a_new_instance_reads_it_THEN_it_is_still_there() {
        KeystoreSessionStorage(context).saveTokens(accessToken = "access-123", refreshToken = "refresh-456")

        val restarted = KeystoreSessionStorage(context)
        assertEquals("access-123", restarted.getAccessToken())
        assertEquals("refresh-456", restarted.getRefreshToken())
    }

    @Test
    fun GIVEN_a_signed_in_phone_WHEN_the_token_is_cleared_THEN_a_new_instance_reads_as_signed_out() {
        val storage = KeystoreSessionStorage(context)
        storage.saveTokens(accessToken = "access-123", refreshToken = "refresh-456")
        storage.clear()

        val restarted = KeystoreSessionStorage(context)
        assertNull(restarted.getAccessToken())
        assertNull(restarted.getRefreshToken())
        assertFalse(tokenFile.exists())
    }

    @Test
    fun GIVEN_a_saved_token_WHEN_the_file_on_disk_is_read_THEN_it_is_in_noBackupFilesDir_and_not_plain_text() {
        KeystoreSessionStorage(context).saveTokens(accessToken = "access-123", refreshToken = "refresh-456")

        assertTrue("Token file should live in noBackupFilesDir", tokenFile.exists())
        val onDisk = tokenFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("Access token must not be stored in plain text", onDisk.contains("access-123"))
        assertFalse("Refresh token must not be stored in plain text", onDisk.contains("refresh-456"))
    }

    @Test
    fun GIVEN_a_token_file_that_cannot_be_decrypted_WHEN_it_is_read_THEN_nobody_is_signed_in_and_the_file_is_dropped() {
        tokenFile.parentFile?.mkdirs()
        tokenFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17))

        val storage = KeystoreSessionStorage(context)
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertFalse(tokenFile.exists())
    }

    @Test
    fun GIVEN_a_member_saved_on_this_phone_WHEN_a_new_instance_reads_them_THEN_they_are_still_there() {
        KeystoreSessionStorage(context).apply {
            saveTokens(accessToken = "access-123")
            saveUser(emma)
        }

        val restarted = KeystoreSessionStorage(context)
        assertEquals(emma, restarted.getUser())
    }

    @Test
    fun GIVEN_a_stored_member_WHEN_only_a_token_is_saved_THEN_a_new_instance_still_reads_them() {
        KeystoreSessionStorage(context).apply {
            saveTokens(accessToken = "access-123")
            saveUser(emma)
        }

        KeystoreSessionStorage(context).saveTokens(accessToken = "access-789")

        val restarted = KeystoreSessionStorage(context)
        assertEquals("access-789", restarted.getAccessToken())
        assertEquals(emma, restarted.getUser())
    }

    @Test
    fun GIVEN_a_saved_member_WHEN_the_file_on_disk_is_read_THEN_their_name_is_not_plain_text() {
        KeystoreSessionStorage(context).apply {
            saveTokens(accessToken = "access-123")
            saveUser(emma)
        }

        val onDisk = tokenFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("The member's name must not be stored in plain text", onDisk.contains("Emma Larsson"))
    }

    @Test
    fun GIVEN_a_signed_in_phone_WHEN_the_storage_is_cleared_THEN_a_new_instance_reads_no_member() {
        val storage = KeystoreSessionStorage(context)
        storage.saveTokens(accessToken = "access-123")
        storage.saveUser(emma)

        storage.clear()

        assertNull(KeystoreSessionStorage(context).getUser())
    }
}
