import XCTest
import UIKit

/// The Compose UI is laid out edge to edge: `HearthScaffold`, `MessageComposer`, `HearthBottomNav`
/// and friends pad `WindowInsets.safeDrawing` themselves. That only works if the SwiftUI shell in
/// `ContentView.swift` hands Compose the *whole* window and leaves the safe area to it. If SwiftUI
/// also keeps the Compose view inside the safe area, the status bar and home indicator space is
/// applied twice, and SwiftUI's own background shows as a white strip above and below (#45).
///
/// Like `LaunchSmokeTests`, this runs inside the host app, against the real key window that
/// `WindowGroup { ContentView() }` built.
final class EdgeToEdgeHostingTests: XCTestCase {

    /// The Compose view covers the key window from edge to edge, status bar and home indicator
    /// included.
    func testComposeRootFillsTheWholeWindow() throws {
        let window = try XCTUnwrap(keyWindow(), "No key window found on the host app.")
        let composeView = try composeRootView(in: window)

        let frameInWindow = composeView.convert(composeView.bounds, to: window)

        XCTAssertEqual(
            frameInWindow, window.bounds,
            "The Compose view is inset inside the window, so the safe area is applied by SwiftUI "
                + "as well as by Compose."
        )
    }

    /// Filling the window must not cost Compose the insets: it still has to keep its content clear
    /// of the notch and the home indicator.
    func testComposeRootStillReceivesTheSafeAreaInsets() throws {
        let window = try XCTUnwrap(keyWindow(), "No key window found on the host app.")
        let composeView = try composeRootView(in: window)

        XCTAssertGreaterThan(window.safeAreaInsets.top, 0, "The test device has no top safe area.")
        XCTAssertEqual(composeView.safeAreaInsets, window.safeAreaInsets)
    }

    // MARK: - Helpers

    /// The view of the controller `MainViewControllerKt.MainViewController()` returned, found as a
    /// descendant of the SwiftUI hosting controller at the window's root.
    private func composeRootView(in window: UIWindow) throws -> UIView {
        let root = try XCTUnwrap(window.rootViewController, "Host window has no root view controller.")
        pumpRunLoop()
        let controllers = descendants(of: root)
        let compose = try XCTUnwrap(
            controllers.first { String(describing: type(of: $0)).contains("Compose") },
            "No Compose view controller under the root; found "
                + controllers.map { String(describing: type(of: $0)) }.joined(separator: ", ")
        )
        return compose.view
    }

    private func descendants(of controller: UIViewController) -> [UIViewController] {
        controller.children.flatMap { [$0] + descendants(of: $0) }
    }

    private func keyWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })
    }

    /// Gives pending SwiftUI and Compose layout a few run-loop turns to settle.
    private func pumpRunLoop(turns: Int = 5, eachFor interval: TimeInterval = 0.05) {
        for _ in 0..<turns {
            RunLoop.current.run(until: Date().addingTimeInterval(interval))
        }
    }
}
