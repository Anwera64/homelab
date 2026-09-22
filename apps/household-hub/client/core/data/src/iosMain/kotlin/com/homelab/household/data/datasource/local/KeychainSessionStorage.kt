package com.homelab.household.data.datasource.local

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import com.homelab.household.data.dto.UserReadDto
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSLock
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Keeps the session — auth tokens and the signed-in member — in the iOS Keychain as a single
 * generic-password item.
 *
 * The item is stored `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: it is never synced to
 * iCloud and never restored onto a new device. That is the Apple equivalent of the Android sibling
 * putting its file in `noBackupFilesDir` — a Keystore key never leaves the device, so a restored
 * backup could not be decrypted anyway, and tokens should not outlive the device either way.
 *
 * Concurrency: this class keeps **no in-memory cache** — every call reads through to the Keychain,
 * which is itself thread-safe and is the single source of truth. A cache would have to be
 * invalidated by writes from other instances of this class (the Android sibling gets away with it
 * only because the app builds one instance), and nothing here is hot enough to need one.
 * Neither [saveTokens] nor [saveUser] is a single Keychain call: each has to read the rest of the
 * stored payload before writing its own part back, so that saving a token cannot drop the member
 * (or a `null` refresh token the stored one). Both read-modify-writes are held under an [NSLock],
 * so two concurrent saves cannot interleave and lose what the other kept.
 * The lock is per-instance, so it makes concurrent *callers* safe, not concurrent processes — an
 * iOS app has exactly one process, so that is the whole of the problem.
 *
 * Nothing thrown by the Keychain or by JSON parsing escapes these four methods; anything
 * unreadable is deleted and reads as signed out.
 */
class KeychainSessionStorage : StoredSessionLocalDataSource {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = NSLock()

    override fun saveTokens(accessToken: String, refreshToken: String?) {
        locked {
            // A null refresh token means "leave the stored one alone", and the member is never
            // this call's business, so merge both before writing.
            val stored = readPayload()
            write(
                SessionDiskPayload(
                    accessToken = accessToken,
                    refreshToken = refreshToken ?: stored?.refreshToken,
                    user = stored?.user
                )
            )
        }
    }

    override fun getAccessToken(): String? = locked { readPayload()?.accessToken }

    override fun getRefreshToken(): String? = locked { readPayload()?.refreshToken }

    override fun saveUser(user: UserReadDto?) {
        locked {
            val stored = readPayload()
            write(
                SessionDiskPayload(
                    accessToken = stored?.accessToken,
                    refreshToken = stored?.refreshToken,
                    user = user
                )
            )
        }
    }

    override fun getUser(): UserReadDto? = locked { readPayload()?.user }

    override fun clear() {
        locked {
            try {
                Keychain.delete()
            } catch (_: Throwable) {
            }
        }
    }

    /** The one place anything is written; a Keychain that refuses leaves the item as it was. */
    private fun write(payload: SessionDiskPayload) {
        try {
            Keychain.write(json.encodeToString(payload).encodeToByteArray())
        } catch (_: Throwable) {
        }
    }

    /** Reads the stored payload, deleting (and reporting signed out) anything unreadable. */
    private fun readPayload(): SessionDiskPayload? {
        val bytes = try {
            Keychain.read()
        } catch (_: Throwable) {
            null
        } ?: return null

        return try {
            json.decodeFromString<SessionDiskPayload>(bytes.decodeToString())
        } catch (_: Throwable) {
            try {
                Keychain.delete()
            } catch (_: Throwable) {
            }
            null
        }
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

/**
 * The raw generic-password item behind [KeychainSessionStorage].
 *
 * Memory management: every CoreFoundation object created here is created with a `Create` function
 * (so we own a +1 reference) and handed to `defer`, which releases it when the enclosing
 * `memScoped` exits. Kotlin/Native does not expose `CFRelease`, so the release is spelled
 * `CFBridgingRelease`, which hands the +1 to ARC. The results of [SecItemCopyMatching] are also
 * +1 — the "Copy" in the name — and are released explicitly in a `finally`.
 */
@OptIn(ExperimentalForeignApi::class)
internal object Keychain {

    private const val SERVICE = "com.homelab.household.tokens"
    private const val ACCOUNT = "auth_tokens"

    fun read(): ByteArray? = memScoped {
        val query = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfString(SERVICE),
            kSecAttrAccount to cfString(ACCOUNT),
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne
        )
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        if (status != errSecSuccess) return@memScoped null

        // SecItemCopyMatching returns a +1 reference that we own.
        val data = result.value ?: return@memScoped null
        try {
            @Suppress("UNCHECKED_CAST")
            (data as CFDataRef).toByteArray()
        } finally {
            CFBridgingRelease(data)
        }
    }

    /** Adds the item, or replaces the data of the one already there. */
    fun write(value: ByteArray): Boolean = memScoped {
        val data = cfData(value)
        val query = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfString(SERVICE),
            kSecAttrAccount to cfString(ACCOUNT)
        )
        val updated = SecItemUpdate(query, cfDictionary(kSecValueData to data))
        if (updated == errSecSuccess) return@memScoped true
        if (updated != errSecItemNotFound) return@memScoped false

        val attributes = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfString(SERVICE),
            kSecAttrAccount to cfString(ACCOUNT),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData to data
        )
        SecItemAdd(attributes, null) == errSecSuccess
    }

    fun delete(): Boolean = memScoped {
        val query = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfString(SERVICE),
            kSecAttrAccount to cfString(ACCOUNT)
        )
        val status = SecItemDelete(query)
        status == errSecSuccess || status == errSecItemNotFound
    }

    /**
     * Builds a CFDictionary whose keys and values are borrowed: `kCFType*CallBacks` makes the
     * dictionary retain them for its own lifetime, and the dictionary itself is released on exit.
     */
    private fun MemScope.cfDictionary(
        vararg entries: Pair<CFStringRef?, COpaquePointer?>
    ): CFDictionaryRef? {
        val keys = allocArray<COpaquePointerVar>(entries.size)
        val values = allocArray<COpaquePointerVar>(entries.size)
        entries.forEachIndexed { index, (key, value) ->
            keys[index] = key
            values[index] = value
        }
        val dictionary = CFDictionaryCreate(
            null,
            keys,
            values,
            entries.size.toLong(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        )
        defer { CFBridgingRelease(dictionary) }
        return dictionary
    }

    private fun MemScope.cfString(value: String): CFStringRef? {
        val string = CFStringCreateWithCString(null, value, kCFStringEncodingUTF8)
        defer { CFBridgingRelease(string) }
        return string
    }

    private fun MemScope.cfData(value: ByteArray): COpaquePointer? {
        val buffer = allocArray<UByteVar>(value.size)
        value.forEachIndexed { index, byte -> buffer[index] = byte.toUByte() }
        val data = CFDataCreate(null, buffer, value.size.toLong())
        defer { CFBridgingRelease(data) }
        return data
    }

    private fun CFDataRef.toByteArray(): ByteArray {
        val length = CFDataGetLength(this).toInt()
        if (length <= 0) return ByteArray(0)
        val bytes = CFDataGetBytePtr(this) ?: return ByteArray(0)
        return ByteArray(length) { bytes[it].toByte() }
    }

}
