package com.homelab.household.data.datasource.local

import com.homelab.household.data.dto.UserReadDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The JVM's [StoredSessionLocalDataSource]. A second instance stands in for a restart: what one wrote, the
 * next one must read, because that is the whole job.
 */
class FileSessionStorageTest {
    private val testDir = "build/test-token-storage"

    private fun storage() = FileSessionStorage(storageDir = testDir)

    private val emma =
        UserReadDto(
            id = "emma",
            full_name = "Emma",
            is_admin = true,
            is_active = true,
            avatar_color = "#3C6E4E",
        )

    @BeforeEach
    fun signedOutToStart() {
        storage().clear()
    }

    @Test
    fun `GIVEN a token saved on this machine WHEN a new instance reads it THEN it is still there`() {
        // GIVEN
        storage().saveTokens(accessToken = "access-123", refreshToken = "refresh-456")

        // WHEN
        val restarted = storage()

        // THEN
        assertEquals("access-123", restarted.getAccessToken())
        assertEquals("refresh-456", restarted.getRefreshToken())
    }

    @Test
    fun `GIVEN a signed-in machine WHEN the token is cleared THEN a new instance reads as signed out`() {
        // GIVEN
        val signedIn = storage()
        signedIn.saveTokens(accessToken = "access-123", refreshToken = "refresh-456")

        // WHEN
        signedIn.clear()

        // THEN
        val restarted = storage()
        assertNull(restarted.getAccessToken())
        assertNull(restarted.getRefreshToken())
    }

    @Test
    fun `GIVEN a kept refresh token WHEN only an access token is saved THEN the refresh token is left alone`() {
        // GIVEN
        storage().saveTokens(accessToken = "access-123", refreshToken = "refresh-456")

        // WHEN
        storage().saveTokens(accessToken = "access-789")

        // THEN
        val restarted = storage()
        assertEquals("access-789", restarted.getAccessToken())
        assertEquals("refresh-456", restarted.getRefreshToken())
    }

    @Test
    fun `GIVEN a member saved on this machine WHEN a new instance reads them THEN they are still there`() {
        // GIVEN
        storage().apply {
            saveTokens(accessToken = "access-123")
            saveUser(emma)
        }

        // WHEN
        val restarted = storage()

        // THEN
        assertEquals(emma, restarted.getUser())
    }

    @Test
    fun `GIVEN a stored member WHEN only a token is saved THEN a new instance still reads them`() {
        // GIVEN
        storage().apply {
            saveTokens(accessToken = "access-123")
            saveUser(emma)
        }

        // WHEN
        storage().saveTokens(accessToken = "access-789")

        // THEN
        val restarted = storage()
        assertEquals("access-789", restarted.getAccessToken())
        assertEquals(emma, restarted.getUser())
    }

    @Test
    fun `GIVEN a signed-in machine WHEN the storage is cleared THEN a new instance reads no member`() {
        // GIVEN
        val signedIn = storage()
        signedIn.saveTokens(accessToken = "access-123")
        signedIn.saveUser(emma)

        // WHEN
        signedIn.clear()

        // THEN
        assertNull(storage().getUser())
    }
}
