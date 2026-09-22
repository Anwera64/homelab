package com.homelab.household.data.datasource.local

import com.homelab.household.data.dto.UserReadDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The contract every [StoredSessionLocalDataSource] shares, on the implementation that has no
 * platform underneath it. `FileSessionStorageTest`, `KeystoreSessionStorageTest` and
 * `KeychainSessionStorageTests` prove the same rules against a real file, Keystore and Keychain.
 *
 * The rule that earns its own test is the middle one: a PIN change saves a fresh token and nothing
 * else (`MembersRepositoryImpl.changePin`), and a storage that dropped the member there would empty
 * the profile on the next cold start without anything else going wrong.
 */
class InMemorySessionStorageTest {
    private val emma =
        UserReadDto(
            id = "emma",
            full_name = "Emma",
            is_admin = true,
            is_active = true,
            avatar_color = "#3C6E4E",
        )

    private fun storage() = InMemorySessionStorage()

    @Test
    fun `GIVEN a saved member WHEN the storage is read THEN it is them`() {
        // GIVEN
        val storage = storage().apply { saveTokens("access-123") }

        // WHEN
        storage.saveUser(emma)

        // THEN
        assertEquals(emma, storage.getUser())
    }

    @Test
    fun `GIVEN a saved member WHEN only a token is saved THEN the member is left alone`() {
        // GIVEN
        val storage =
            storage().apply {
                saveTokens("access-123")
                saveUser(emma)
            }

        // WHEN
        storage.saveTokens("access-789")

        // THEN
        assertEquals("access-789", storage.getAccessToken())
        assertEquals(emma, storage.getUser())
    }

    @Test
    fun `GIVEN a saved member WHEN the storage is cleared THEN the member and the token are both gone`() {
        // GIVEN
        val storage =
            storage().apply {
                saveTokens("access-123", "refresh-456")
                saveUser(emma)
            }

        // WHEN
        storage.clear()

        // THEN
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertNull(storage.getUser())
    }

    @Test
    fun `GIVEN a saved member WHEN nobody is saved over them THEN they are forgotten`() {
        // GIVEN
        val storage =
            storage().apply {
                saveTokens("access-123")
                saveUser(emma)
            }

        // WHEN
        storage.saveUser(null)

        // THEN
        assertNull(storage.getUser())
        assertEquals("access-123", storage.getAccessToken())
    }
}
