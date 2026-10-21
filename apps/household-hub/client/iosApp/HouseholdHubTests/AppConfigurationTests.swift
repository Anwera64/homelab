import XCTest

/// The hub and log collector the app reads from `Info.plist` (`MainViewController.kt`).
///
/// project.yml fills `HubBaseUrl` and `TelemetryBaseUrl` per configuration (#134). These tests run
/// hosted in a Debug build, so they see the dev app: its hub is the dev hub, and its name and
/// bundle ID keep it apart from the household's app on the same phone. A key missing from the
/// plist would stop the app at start, which is why the values are checked here and not only in
/// project.yml.
final class AppConfigurationTests: XCTestCase {

    func testDevAppTalksToTheDevHub() throws {
        let hub = try XCTUnwrap(Bundle.main.object(forInfoDictionaryKey: "HubBaseUrl") as? String)
        XCTAssertEqual(hub, "https://hub-dev.spicy-llama.duckdns.org")
    }

    func testDevAppSendsItsLogsToTheDevCollector() throws {
        let telemetry = try XCTUnwrap(Bundle.main.object(forInfoDictionaryKey: "TelemetryBaseUrl") as? String)
        XCTAssertEqual(telemetry, "https://telemetry-dev.spicy-llama.duckdns.org")
    }

    /// XcodeGen's own defaults are "1.0" and "1"; the app must carry app.version and its build number.
    func testAppCarriesItsReleaseVersionAndBuildNumber() throws {
        let version = try XCTUnwrap(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String)
        let build = try XCTUnwrap(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String)
        XCTAssertNotNil(version.range(of: #"^\d+\.\d+\.\d+$"#, options: .regularExpression), "version \(version)")
        XCTAssertNotEqual(version, "1.0")
        XCTAssertNotEqual(build, "1")
    }

    func testDevAppIsNamedAndIdentifiedApartFromTheHouseholdsApp() {
        XCTAssertEqual(Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String, "HyggeHub Dev")
        XCTAssertEqual(Bundle.main.bundleIdentifier, "com.homelab.household.dev")
    }
}
