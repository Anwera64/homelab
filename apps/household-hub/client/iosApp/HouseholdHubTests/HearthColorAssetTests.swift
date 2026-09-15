import XCTest

/// The proof that a `.xcassets` colour set added under `HouseholdHub/` actually reaches the built
/// app bundle through XcodeGen.
///
/// `HouseholdHubTests` is a **unit-test bundle hosted by the app** (see
/// `KeychainTokenStorageTests` for the long version of why that matters here): `TEST_HOST` points
/// at `HouseholdHub.app`, so inside this process `Bundle.main` — and therefore
/// `UIColor(named:)`, which always looks in the main bundle — resolves against the *shipping app's*
/// asset catalog, not the test bundle's own. A colour set that only exists in some other target, or
/// that XcodeGen's `sources` glob failed to pick up, would make `UIColor(named:)` return `nil` here
/// exactly as it would in the running app. That is the risk this cycle exists to retire, before any
/// launch-screen or app-icon asset is built on top of the same mechanism.
///
/// `HearthCanvas` mirrors `DayColors.canvas` / `NightColors.canvas` in
/// `composeApp/src/commonMain/kotlin/com/homelab/household/app/theme/HearthColors.kt`, and the
/// Android `hearth_canvas` entries in `res/values/colors.xml` / `res/values-night/colors.xml`:
/// `#F5F2EB` in light, `#100F0E` in dark.
final class HearthColorAssetTests: XCTestCase {

    func testHearthCanvasResolvesInLightAppearance() throws {
        let color = try hearthCanvasColor(style: .light)

        assertColor(color, red: 0xF5, green: 0xF2, blue: 0xEB)
    }

    func testHearthCanvasResolvesInDarkAppearance() throws {
        let color = try hearthCanvasColor(style: .dark)

        assertColor(color, red: 0x10, green: 0x0F, blue: 0x0E)
    }

    // MARK: - Helpers

    /// Looks up `HearthCanvas` in the app's asset catalog and resolves it for `style`.
    ///
    /// Unwrapping here, rather than at each call site, keeps the failure message specific: a `nil`
    /// name lookup means the colour set is missing (or misnamed, or not a member of the
    /// `HouseholdHub` target); it must never surface as a bare `XCTAssertNil` or a crash on force
    /// unwrap.
    private func hearthCanvasColor(
        style: UIUserInterfaceStyle,
        file: StaticString = #filePath,
        line: UInt = #line
    ) throws -> UIColor {
        let base = try XCTUnwrap(
            UIColor(named: "HearthCanvas"),
            "No color set named \"HearthCanvas\" in the app's asset catalog "
                + "(iosApp/HouseholdHub/Assets.xcassets/HearthCanvas.colorset) — "
                + "or it exists but is not a member of the HouseholdHub target.",
            file: file,
            line: line
        )
        return base.resolvedColor(with: traitCollection(for: style))
    }

    /// `UITraitCollection(userInterfaceStyle:)` is deprecated from iOS 17 in favour of the trait
    /// builder closure, but the deployment target is iOS 15 — so the branch has to exist, and it
    /// has to live in exactly one place rather than at every call site.
    private func traitCollection(for style: UIUserInterfaceStyle) -> UITraitCollection {
        if #available(iOS 17.0, *) {
            return UITraitCollection { mutableTraits in
                mutableTraits.userInterfaceStyle = style
            }
        } else {
            return UITraitCollection(userInterfaceStyle: style)
        }
    }

    /// Compares `color` against an 8-bit-per-channel hex triple with a half-quantisation-step
    /// tolerance (`0.5/255`). `UIColor` components round-trip through a colour space on their way
    /// out of `getRed:green:blue:alpha:`, so exact `==` against a hand-computed `Double` is a flake
    /// waiting to happen; half a step is tight enough to catch a wrong asset while tolerating that
    /// round trip. `file`/`line` are forwarded so a failure points at the `testHearthCanvas...`
    /// call site, not this helper.
    private func assertColor(
        _ color: UIColor,
        red: UInt8,
        green: UInt8,
        blue: UInt8,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        var r: CGFloat = 0
        var g: CGFloat = 0
        var b: CGFloat = 0
        var a: CGFloat = 0
        color.getRed(&r, green: &g, blue: &b, alpha: &a)

        let tolerance: CGFloat = 0.5 / 255.0
        let expectedRed = CGFloat(red) / 255.0
        let expectedGreen = CGFloat(green) / 255.0
        let expectedBlue = CGFloat(blue) / 255.0

        XCTAssertEqual(
            r, expectedRed, accuracy: tolerance,
            "red component \(r) was not within \(tolerance) of expected \(expectedRed) "
                + "(hex #\(String(format: "%02X%02X%02X", red, green, blue)))",
            file: file, line: line
        )
        XCTAssertEqual(
            g, expectedGreen, accuracy: tolerance,
            "green component \(g) was not within \(tolerance) of expected \(expectedGreen) "
                + "(hex #\(String(format: "%02X%02X%02X", red, green, blue)))",
            file: file, line: line
        )
        XCTAssertEqual(
            b, expectedBlue, accuracy: tolerance,
            "blue component \(b) was not within \(tolerance) of expected \(expectedBlue) "
                + "(hex #\(String(format: "%02X%02X%02X", red, green, blue)))",
            file: file, line: line
        )
        XCTAssertEqual(
            a, 1.0, accuracy: tolerance,
            "alpha component \(a) was not fully opaque",
            file: file, line: line
        )
    }
}
