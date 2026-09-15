import XCTest

/// The app icon actool extracts from `Assets.xcassets/AppIcon.appiconset` into the bundle root.
///
/// Today the appiconset's `Contents.json` is a stub cycle 1 added only to satisfy actool's hard
/// requirement that a catalog containing *any* set must also contain one matching
/// `ASSETCATALOG_COMPILER_APPICON_NAME` (`AppIcon`, injected by XcodeGen's iOS-application
/// preset): one `{"idiom":"universal","platform":"ios","size":"1024x1024"}` entry with no
/// `filename` and no backing image file. actool accepts that as an unfilled slot, not a missing
/// set, and the app currently ships with the blank system-default icon.
///
/// Unlike every other Hearth asset in this suite, an app icon is **not** looked up with
/// `UIImage(named: "AppIcon")` — that API is for asset-catalog *image sets* addressed by their
/// catalog name at runtime; an app icon is compiled out of the catalog into loose files in the
/// bundle root, and is not addressable by its catalog name. Instead actool writes the real,
/// on-disk file names it produced into `Info.plist` under
/// `CFBundleIcons.CFBundlePrimaryIcon.CFBundleIconFiles`, and it only writes `CFBundleIcons` at
/// all once it has actually compiled an icon from a filled slot — so `CFBundleIcons`'s mere
/// presence is itself proof a real icon exists, and its absence is this cycle's expected red.
///
/// The icon is asserted opaque and Hearth-primary-green at a corner, sampled clear of the
/// household glyph that sits at the icon's centre — reusing the rasterizing/comparison helpers
/// from `PixelSampling.swift` (factored out of `HearthLaunchTileTests` in cycle 2).
/// `DayColors.primary` (`#3C6E4E`) is used in every appearance — an app icon is the app's mark,
/// not UI, so unlike `HearthCanvas` / `HearthLaunchTile` there is no dark variant to check.
///
/// One caveat worth being explicit about: Apple's actual "no alpha channel" rule is a structural
/// check on the source PNG at export/submission time (a fully-opaque-everywhere alpha channel is
/// still rejected, purely for existing). Rasterizing through `rasterizedPixel` normalizes into an
/// opaque-by-construction bitmap context, so a pixel-sampling test like this one cannot see that
/// structural distinction — what it *can* catch is a corner that renders with visible
/// transparency, which is the cheap, useful proxy available at this layer.
final class AppIconTests: XCTestCase {

    func testAppIconIsCompiledIntoTheBundle() throws {
        _ = try appIconImage()
    }

    func testAppIconCornerIsOpaqueHearthPrimaryGreen() throws {
        let image = try appIconImage()
        let corner = CGPoint(x: iconCanvasSize * 0.1, y: iconCanvasSize * 0.1)
        let pixel = try rasterizedPixel(of: image, at: corner, canvasSize: iconCanvasSize)

        assertPixel(pixel, hex: (0x3C, 0x6E, 0x4E))
        XCTAssertEqual(
            pixel.a, 255,
            "Corner pixel alpha was \(pixel.a), not fully opaque — an app icon must not carry any "
                + "visible transparency."
        )
    }

    // MARK: - Geometry

    /// Sampled 10% in from the edge on the 1024-unit icon, diagonally opposite where the centred
    /// household glyph lives — not verified against the real artwork yet, since (like
    /// `HearthLaunchTileTests`'s tile sample point when it was written in cycle 2) the real icon
    /// does not exist this cycle; only the stub does. The next green run against the real
    /// `AppIcon` is the actual proof this point clears the glyph.
    private let iconCanvasSize: CGFloat = 1024

    // MARK: - Helpers

    /// actool records the compiled icon's file names in `Info.plist`, not the asset catalog name —
    /// see the class doc comment for why `UIImage(named: "AppIcon")` is not the right lookup.
    /// `CFBundleIconFiles` is ordered smallest-to-largest by convention, so the last entry is the
    /// largest/most detailed rendition actool produced — the one worth sampling.
    private func appIconImage(file: StaticString = #filePath, line: UInt = #line) throws -> UIImage {
        let icons = try XCTUnwrap(
            Bundle.main.infoDictionary?["CFBundleIcons"] as? [String: Any],
            "Info.plist has no CFBundleIcons — actool has not compiled a real app icon. Check "
                + "iosApp/HouseholdHub/Assets.xcassets/AppIcon.appiconset has a filled 1024x1024 slot.",
            file: file, line: line
        )
        let primaryIcon = try XCTUnwrap(
            icons["CFBundlePrimaryIcon"] as? [String: Any],
            "CFBundleIcons has no CFBundlePrimaryIcon.",
            file: file, line: line
        )
        let iconFiles = try XCTUnwrap(
            primaryIcon["CFBundleIconFiles"] as? [String],
            "CFBundlePrimaryIcon has no CFBundleIconFiles array.",
            file: file, line: line
        )
        let lastFileName = try XCTUnwrap(
            iconFiles.last,
            "CFBundleIconFiles is empty.",
            file: file, line: line
        )
        return try XCTUnwrap(
            UIImage(named: lastFileName),
            "CFBundleIconFiles names \"\(lastFileName)\", but UIImage(named:) could not load it "
                + "from the app bundle.",
            file: file, line: line
        )
    }
}
