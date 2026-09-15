import XCTest
import HouseholdHubKit

/// The real-Keychain proof for `KeychainTokenStorage`.
///
/// This is a **unit-test bundle hosted by the app** (`TEST_HOST` is `HouseholdHub.app`), not a UI
/// test. That is the whole point: the code runs inside the app's process, so `SecItemAdd` and
/// friends see the app's entitlements. The same calls from a bare Kotlin/Native test binary — what
/// `./gradlew :core:data:iosSimulatorArm64Test` would produce — fail with
/// `errSecNotAvailable (-25291)`, because that binary is not an app bundle and has no keychain
/// access group. Nothing is faked here: every assertion below is a round trip through the
/// simulator's real Keychain.
///
/// `KeychainTokenProbe` is a few lines of Kotlin in `:iosApp` that hold one `KeychainTokenStorage`
/// each and forward the four `TokenStorage` methods, so that the Objective-C header of
/// `HouseholdHubKit` does not have to export the whole of `:core:data`.
final class KeychainTokenStorageTests: XCTestCase {

    override func setUp() {
        super.setUp()
        KeychainTokenProbe().clear()
    }

    override func tearDown() {
        KeychainTokenProbe().clear()
        super.tearDown()
    }

    func testSavedTokensReadBack() {
        let storage = KeychainTokenProbe()

        storage.save(accessToken: "access-1", refreshToken: "refresh-1")

        XCTAssertEqual(storage.accessToken(), "access-1")
        XCTAssertEqual(storage.refreshToken(), "refresh-1")
    }

    /// The Keychain, not an in-memory field, is the source of truth: a fresh instance — which is
    /// what the app gets after a relaunch — sees what an earlier one wrote.
    func testSecondInstanceReadsWhatTheFirstWrote() {
        KeychainTokenProbe().save(accessToken: "access-2", refreshToken: "refresh-2")

        let reader = KeychainTokenProbe()

        XCTAssertEqual(reader.accessToken(), "access-2")
        XCTAssertEqual(reader.refreshToken(), "refresh-2")
    }

    /// A refresh happens with no new refresh token, and must not wipe the stored one.
    func testNullRefreshTokenKeepsTheStoredOne() {
        let storage = KeychainTokenProbe()
        storage.save(accessToken: "access-3", refreshToken: "refresh-3")

        storage.save(accessToken: "access-3-rotated", refreshToken: nil)

        XCTAssertEqual(storage.accessToken(), "access-3-rotated")
        XCTAssertEqual(storage.refreshToken(), "refresh-3")
        // And the merge is in the Keychain item, not in this instance.
        XCTAssertEqual(KeychainTokenProbe().refreshToken(), "refresh-3")
    }

    /// A write replaces the item rather than adding a second one, so there is nothing stale left
    /// behind for a later read to find.
    func testSavingTwiceReplacesTheItem() {
        let storage = KeychainTokenProbe()
        storage.save(accessToken: "access-old", refreshToken: "refresh-old")

        storage.save(accessToken: "access-new", refreshToken: "refresh-new")

        XCTAssertEqual(KeychainTokenProbe().accessToken(), "access-new")
        XCTAssertEqual(KeychainTokenProbe().refreshToken(), "refresh-new")
    }

    func testClearEmptiesTheKeychain() {
        let storage = KeychainTokenProbe()
        storage.save(accessToken: "access-4", refreshToken: "refresh-4")

        storage.clear()

        XCTAssertNil(storage.accessToken())
        XCTAssertNil(storage.refreshToken())
        XCTAssertNil(KeychainTokenProbe().accessToken())
    }

    /// Signed out is the resting state, and reads there are `nil`, not a thrown error.
    func testReadsWithNothingStoredAreNil() {
        let storage = KeychainTokenProbe()

        XCTAssertNil(storage.accessToken())
        XCTAssertNil(storage.refreshToken())
        // Clearing an already-empty item is a no-op, not a failure.
        storage.clear()
        XCTAssertNil(storage.accessToken())
    }
}
