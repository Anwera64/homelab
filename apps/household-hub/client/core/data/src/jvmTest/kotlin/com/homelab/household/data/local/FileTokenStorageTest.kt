package com.homelab.household.data.local

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FileTokenStorageTest {

    @Test
    fun save_tokens_persists_across_storage_instances() {
        val testDir = "build/test-token-storage"
        val storage1 = FileTokenStorage(storageDir = testDir)
        storage1.clear()

        storage1.saveTokens(accessToken = "test-access-token-123", refreshToken = "test-refresh-token-456")
        assertEquals("test-access-token-123", storage1.getAccessToken())
        assertEquals("test-refresh-token-456", storage1.getRefreshToken())

        // Create a separate instance simulating app restart
        val storage2 = FileTokenStorage(storageDir = testDir)
        assertEquals("test-access-token-123", storage2.getAccessToken())
        assertEquals("test-refresh-token-456", storage2.getRefreshToken())

        // Clear and verify both instances
        storage2.clear()
        assertNull(storage2.getAccessToken())
        assertNull(storage2.getRefreshToken())

        val storage3 = FileTokenStorage(storageDir = testDir)
        assertNull(storage3.getAccessToken())
        assertNull(storage3.getRefreshToken())
    }
}
