import UIKit
import XCTest

/// Shared rasterizing/pixel-comparison machinery for the Hearth image-asset tests.
///
/// Factored out of `HearthLaunchTileTests` (cycle 2) into free functions rather than instance
/// methods on one `XCTestCase` subclass, so `AppIconTests` (cycle 5) can reuse them exactly rather
/// than copying them a second time. `XCTUnwrap`/`XCTAssertTrue` are themselves free functions, so
/// there is nothing `XCTestCase`-specific either helper needs.

/// Rasterizes `image` into a `canvasSize` × `canvasSize` RGBA8 bitmap at scale 1 and reads back the
/// pixel at `point` (in the same unit space as `canvasSize`, i.e. the image's own drawing
/// coordinates — not points or device pixels).
///
/// The bitmap uses `.premultipliedLast` in `CGColorSpaceCreateDeviceRGB()`, which Quartz
/// guarantees stores components in `R, G, B, A` byte order with no separate byte-order flag — so
/// the read below needs no per-platform byte-order handling. `CGContext`'s native space has origin
/// bottom-left with y increasing upward (PDF-style), while `UIImage.draw(in:)` — like all UIKit
/// drawing — assumes origin top-left with y increasing downward; the translate + negative-y-scale
/// before `UIGraphicsPushContext` converts one into the other so that, after drawing, offset 0 in
/// the buffer is the *top-left* pixel and `point.y` grows downward, matching how `point` is written
/// at each call site. Letting `CGContext` allocate its own backing store (`data: nil`) avoids
/// holding an escaping pointer into a Swift-managed buffer across the draw calls.
func rasterizedPixel(
    of image: UIImage,
    at point: CGPoint,
    canvasSize: CGFloat,
    file: StaticString = #filePath, line: UInt = #line
) throws -> (r: UInt8, g: UInt8, b: UInt8, a: UInt8) {
    let width = Int(canvasSize)
    let height = Int(canvasSize)
    let bytesPerPixel = 4
    let bytesPerRow = bytesPerPixel * width

    let context = try XCTUnwrap(
        CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ),
        "Could not create an RGBA8 bitmap context to rasterize the image into.",
        file: file, line: line
    )

    context.translateBy(x: 0, y: canvasSize)
    context.scaleBy(x: 1, y: -1)

    UIGraphicsPushContext(context)
    image.draw(in: CGRect(x: 0, y: 0, width: canvasSize, height: canvasSize))
    UIGraphicsPopContext()

    let buffer = try XCTUnwrap(
        context.data,
        "Bitmap context produced no backing store.",
        file: file, line: line
    ).assumingMemoryBound(to: UInt8.self)

    let offset = Int(point.y) * bytesPerRow + Int(point.x) * bytesPerPixel
    return (buffer[offset], buffer[offset + 1], buffer[offset + 2], buffer[offset + 3])
}

/// Compares a sampled pixel against an 8-bit-per-channel hex triple with `tolerance` (default 8,
/// i.e. ~3%). Unlike a flat `UIColor` read straight out of `getRed:green:blue:alpha:` (see
/// `HearthColorAssetTests.assertColor`'s half-quantisation-step tolerance), a rasterized *vector*
/// asset also carries antialiasing and colour-management slop from the draw and the DeviceRGB
/// conversion, so a tighter tolerance would flake on correct assets. Also asserts alpha is within
/// `tolerance` of fully opaque.
func assertPixel(
    _ pixel: (r: UInt8, g: UInt8, b: UInt8, a: UInt8),
    hex: (r: UInt8, g: UInt8, b: UInt8),
    tolerance: UInt8 = 8,
    _ message: String = "",
    file: StaticString = #filePath, line: UInt = #line
) {
    func closeEnough(_ actual: UInt8, _ expected: UInt8) -> Bool {
        abs(Int(actual) - Int(expected)) <= Int(tolerance)
    }

    let hexString = String(format: "#%02X%02X%02X", hex.r, hex.g, hex.b)
    let actualString = String(format: "#%02X%02X%02X", pixel.r, pixel.g, pixel.b)
    let prefix = message.isEmpty ? "" : "\(message): "

    XCTAssertTrue(
        closeEnough(pixel.r, hex.r) && closeEnough(pixel.g, hex.g) && closeEnough(pixel.b, hex.b),
        "\(prefix)sampled colour \(actualString) was not within \(tolerance)/255 per channel "
            + "of expected \(hexString)",
        file: file, line: line
    )
    XCTAssertTrue(
        closeEnough(pixel.a, 255),
        "\(prefix)sampled alpha \(pixel.a) was not within \(tolerance)/255 of fully opaque",
        file: file, line: line
    )
}
