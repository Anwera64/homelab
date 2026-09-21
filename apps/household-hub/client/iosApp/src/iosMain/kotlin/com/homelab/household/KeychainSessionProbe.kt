package com.homelab.household

import com.homelab.household.data.datasource.local.KeychainSessionStorage
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource

/**
 * Test support: the narrowest possible Swift-visible window onto [KeychainSessionStorage].
 *
 * Why it exists at all: the Keychain cannot be proved from a bare Kotlin/Native test binary — one
 * gets `errSecNotAvailable (-25291)`, because Gradle runs it as a plain Mach-O process with no app
 * bundle and therefore no keychain-access-group entitlement. The only place on this platform where
 * the real Keychain answers is inside a signed app, so the proof is an XCTest bundle *hosted by
 * this app* (`HouseholdHubTests`), running in the app's process and under the app's entitlements.
 *
 * Why a probe rather than `export(project(":core:data"))` on the framework: exporting the data
 * layer would put every public type in `:core:data` — repositories, DTOs, the whole storage
 * surface — into `HouseholdHubKit`'s generated Objective-C header, where it becomes API that Swift
 * can reach for and that we would have to keep stable. The cost of this class instead is a handful
 * of lines of test-support code in the shipping binary, which is small, inert (nothing in the app
 * references it) and obvious. Every member below takes and returns only `String`/`Unit`, so no
 * `:core:data` type appears in the exported header either.
 *
 * Each instance wraps its own [KeychainSessionStorage], which is what makes "a second instance reads
 * what the first wrote" a real assertion about the Keychain rather than about a shared field.
 */
class KeychainSessionProbe {

    private val storage: StoredSessionLocalDataSource = KeychainSessionStorage()

    /** [StoredSessionLocalDataSource.saveTokens]; a null [refreshToken] means "keep the stored one". */
    fun save(accessToken: String, refreshToken: String?) {
        storage.saveTokens(accessToken, refreshToken)
    }

    fun accessToken(): String? = storage.getAccessToken()

    fun refreshToken(): String? = storage.getRefreshToken()

    fun clear() {
        storage.clear()
    }
}
