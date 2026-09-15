import XCTest

/// The launch tile — the artwork iOS's static launch screen shows — is the twin of Android's
/// `androidApp/src/main/res/drawable/splash_tile.xml`: a rounded square in the Hearth primary
/// colour holding a household glyph in `onPrimary`. It arrives as a vector (SVG) image set named
/// `HearthLaunchTile` with light and dark variants.
///
/// Like `HearthColorAssetTests`, this runs inside `HouseholdHubTests`, a unit-test bundle hosted
/// by `HouseholdHub.app` (`TEST_HOST`), so `Bundle.main` here is the shipping app's bundle and
/// `UIImage(named:)` resolves against its real asset catalog — not a copy in the test bundle.
///
/// The rasterize-and-sample machinery (`rasterizedPixel(of:at:canvasSize:)`, `assertPixel`) lives
/// in `PixelSampling.swift`, shared with `AppIconTests` (cycle 5) rather than duplicated here.
///
/// Two things are pinned:
///  1. The image set exists in the shipping bundle at all.
///  2. Each appearance is coloured with the right Hearth primary — light `#3C6E4E`, dark
///     `#7FB894` (`DayColors.primary` / `NightColors.primary` in
///     `composeApp/src/commonMain/kotlin/com/homelab/household/app/theme/HearthColors.kt`,
///     mirrored as `hearth_primary` in the Android `res/values{,-night}/colors.xml`). Note the
///     night tile is a *dark* glyph on a *light-green* tile — night's `onPrimary` is `#100F0E`,
///     not white — so it is the tile body, not the glyph, that must read `#7FB894` in dark mode.
final class HearthLaunchTileTests: XCTestCase {

    func testHearthLaunchTileResolvesFromTheAppBundle() throws {
        _ = try hearthLaunchTileImage()
    }

    func testHearthLaunchTileIsPrimaryColouredInLightAppearance() throws {
        let variant = try resolvedVariant(of: try hearthLaunchTileImage(), style: .light)
        let pixel = try rasterizedPixel(of: variant, at: tileSamplePoint, canvasSize: tileCanvasSize)

        assertPixel(pixel, hex: (0x3C, 0x6E, 0x4E))
    }

    func testHearthLaunchTileIsPrimaryColouredInDarkAppearance() throws {
        let variant = try resolvedVariant(of: try hearthLaunchTileImage(), style: .dark)
        let pixel = try rasterizedPixel(of: variant, at: tileSamplePoint, canvasSize: tileCanvasSize)

        assertPixel(pixel, hex: (0x7F, 0xB8, 0x94))
    }

    /// Not a Hearth-asset test: this pins the *machinery* the two tests above depend on — the
    /// render-to-bitmap step, the bottom-left-origin-to-top-left-origin coordinate flip, and the
    /// RGBA byte layout — against a synthetic image with an exactly known colour in each half, so
    /// that a failure in the real tests can be trusted to mean "wrong asset", not "wrong sampling
    /// code". It exercises the same `rasterizedPixel(of:at:canvasSize:)` helper the asset tests
    /// use, sampling near the top and near the bottom, far from the seam so no antialiasing at the
    /// boundary can reach either sample point.
    func testRasterizedPixelHelperReadsKnownSyntheticColours() throws {
        let size = CGSize(width: tileCanvasSize, height: tileCanvasSize)
        let synthetic = UIGraphicsImageRenderer(size: size).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: size.width, height: size.height / 2))
            UIColor.blue.setFill()
            context.fill(CGRect(x: 0, y: size.height / 2, width: size.width, height: size.height / 2))
        }

        let topPixel = try rasterizedPixel(
            of: synthetic, at: CGPoint(x: 120, y: 30), canvasSize: tileCanvasSize
        )
        let bottomPixel = try rasterizedPixel(
            of: synthetic, at: CGPoint(x: 120, y: 210), canvasSize: tileCanvasSize
        )

        assertPixel(topPixel, hex: (0xFF, 0x00, 0x00), tolerance: 2, "top half should read back pure red")
        assertPixel(bottomPixel, hex: (0x00, 0x00, 0xFF), tolerance: 2, "bottom half should read back pure blue")
    }

    // MARK: - Geometry

    /// The tile is drawn on a 240-unit canvas mirroring `splash_tile.xml`: an 80-unit rounded
    /// square (24-unit corner radius) whose outline runs `M104,80 H136 A24,24 0 0 1 160,104
    /// V136 A24,24 0 0 1 136,160 H104 A24,24 0 0 1 80,136 V104 A24,24 0 0 1 104,80 Z` — i.e. flat
    /// edges at x/y ∈ {80, 160} for x/y ∈ [104, 136], rounded only in the four corners — centred
    /// at (120, 120), holding a household glyph in a group translated by (100, 100) and scaled
    /// ×1.6667. The glyph's topmost ink is its roof apex at local (12, 3.8), i.e. canvas
    /// (120, 100 + 3.8 × 1.6667) ≈ (120, 106.3).
    ///
    /// (120, 90) is: inside the square's *flat* top edge (x = 120 ∈ [104, 136], so the boundary
    /// there is the straight line at y = 80, not a rounded corner); 10 units below that edge, and
    /// 16.3 units above the glyph's topmost point. That is comfortably inside solid tile colour,
    /// clear of both the rounded corners and the glyph — verified by this arithmetic against the
    /// Android source geometry, not assumed from the prompt. (The real asset does not exist yet
    /// this cycle, so this point is unverified against actual rendered pixels; that happens for
    /// free the next time these tests run against the real `HearthLaunchTile`.)
    private let tileSamplePoint = CGPoint(x: 120, y: 90)
    private let tileCanvasSize: CGFloat = 240

    // MARK: - Helpers

    /// Looks up `HearthLaunchTile` in the app's asset catalog. Mirrors
    /// `HearthColorAssetTests.hearthCanvasColor`'s unwrap-with-a-specific-message approach: a
    /// `nil` here must read as "asset missing/misnamed/wrong target", not a crash.
    private func hearthLaunchTileImage(
        file: StaticString = #filePath, line: UInt = #line
    ) throws -> UIImage {
        try XCTUnwrap(
            UIImage(named: "HearthLaunchTile"),
            "No image set named \"HearthLaunchTile\" in the app's asset catalog "
                + "(iosApp/HouseholdHub/Assets.xcassets/HearthLaunchTile.imageset) — "
                + "or it exists but is not a member of the HouseholdHub target.",
            file: file, line: line
        )
    }

    /// Resolves the light/dark variant of the image via its `imageAsset`, reusing the
    /// `hearthTraitCollection(for:)` helper factored out of `HearthColorAssetTests` in cycle 1.
    /// A `nil` `imageAsset` means the image set cannot carry more than one appearance at all
    /// (e.g. it was added as a plain single image rather than an appearance-aware set).
    private func resolvedVariant(
        of image: UIImage,
        style: UIUserInterfaceStyle,
        file: StaticString = #filePath, line: UInt = #line
    ) throws -> UIImage {
        let asset = try XCTUnwrap(
            image.imageAsset,
            "HearthLaunchTile has no imageAsset, so it cannot carry a light/dark variant — "
                + "check the image set was created with an \"Appearances\" axis, not as a single image.",
            file: file, line: line
        )
        return asset.image(with: hearthTraitCollection(for: style))
    }
}
