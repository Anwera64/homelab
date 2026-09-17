import XCTest

/// Pins the `Info.plist` key that lets the app reach the hub at all on a home network.
///
/// `HttpClient` talks to `https://hub.spicy-llama.duckdns.org`, which on the LAN this app actually
/// runs on resolves to a private address, not a public one. Since iOS 14, reaching *any* local-
/// network address — even by a public hostname that happens to resolve locally, as here — requires
/// the app to hold `NSLocalNetworkUsageDescription`: a human-readable string iOS shows verbatim in
/// the permission prompt explaining why. Without the key, iOS cannot show that prompt at all, and
/// every local-network connection just fails — which reads exactly like a network or DNS bug, not
/// a missing entitlement, making this an easy failure mode to chase in the wrong place.
///
/// Only presence and non-blankness are asserted, not the exact wording: the string is copy, and
/// copy changes without the underlying requirement changing. (Contrast `LaunchScreenPlistTests`,
/// where the *exact* asset names matter because they are load-bearing identifiers, not prose.)
final class PrivacyDeclarationTests: XCTestCase {

    func testLocalNetworkUsageDescriptionIsPresentAndNonBlank() throws {
        let description = try XCTUnwrap(
            Bundle.main.infoDictionary?["NSLocalNetworkUsageDescription"] as? String,
            "Info.plist has no NSLocalNetworkUsageDescription — without it iOS cannot prompt for "
                + "local-network access, and every connection to the hub's LAN address will simply "
                + "fail. Add it to project.yml's targets.HouseholdHub.info.properties."
        )

        XCTAssertFalse(
            description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            "NSLocalNetworkUsageDescription is present but blank — iOS would show an empty "
                + "permission prompt."
        )
    }
}
