import XCTest
import UIKit
import HouseholdHubKit

/// **A regression guard, not proof of a fix.** Everything asserted here is already true today —
/// there is no known defect being exposed, and there is no red expected when this file is first
/// added. Its value is entirely in the future: catching a regression in the app's start-up path
/// the way Android's `androidTest/LaunchSmokeTest.kt`'s `main_activity_reaches_resumed` catches one
/// there — Koin failing to wire up, or the Compose root failing to compose.
///
/// The iOS start-up path (see `iosApp/src/iosMain/kotlin/com/homelab/household/MainViewController.kt`
/// and `iosApp/HouseholdHub/{HouseholdHubApp,ContentView}.swift`):
///  1. `HouseholdHubApp.init()` calls `MainViewControllerKt.startHouseholdHub()` once, eagerly,
///     before any window exists.
///  2. `WindowGroup { ContentView() }` builds `ComposeView`, whose `makeUIViewController(context:)`
///     calls `MainViewControllerKt.MainViewController()`, which calls `startHouseholdHub()` again
///     (unconditionally, no coordination with step 1) and returns the Compose root.
///  3. `startHouseholdHub()` is documented in `MainViewController.kt` as safe to call any number of
///     times, from any thread, because it does nothing but touch `private val koin by lazy { ... }`
///     — Kotlin's default `lazy` is synchronized, and its initializer *is* `HouseholdHubSdk.init()`
///     (Koin's `startKoin()`). A second touch of an already-initialized `lazy` is a no-op; it is
///     specifically this property that makes `KoinAppAlreadyStartedException` "unreachable no
///     matter what the Swift side does," in that file's own words.
///
/// Because this suite runs inside a unit-test bundle hosted by `HouseholdHub.app` (`TEST_HOST`),
/// steps 1 and 2 have already happened for real by the time any test method below runs: there is a
/// live `HouseholdHubApp` with a real key window showing real Compose content. Nothing here is
/// faked or run in isolation.
final class LaunchSmokeTests: XCTestCase {

    /// Pins the exact claim `MainViewController.kt`'s doc comment makes: calling
    /// `startHouseholdHub()` again does not throw. By the time this method runs the host app's
    /// `init()` has already called it once (making this at least the second call overall); calling
    /// it twice more here pins the contract at the point that actually matters — repeatedly, from
    /// a test's perspective, not just "once more."
    ///
    /// `startHouseholdHub()` carries no `@Throws` on the Kotlin side. If a future edit broke the
    /// `lazy` (e.g. replaced it with an unconditional `HouseholdHubSdk.init()` call) so that a
    /// second call reached Koin's `startKoin()` again, it would throw
    /// `KoinAppAlreadyStartedException` — and Kotlin/Native's interop rule for a function with no
    /// `@Throws` is to terminate the process on an uncaught exception, not hand Swift a catchable
    /// `Error`. So a regression here would not surface as an ordinary `XCTAssertXXX failed` line on
    /// this method: the test process would crash *during* `testStartHouseholdHubCalledTwiceIsANoOp`,
    /// and that crash — reported by Xcode as this test crashing, not cleanly failing — is the
    /// signal to look for.
    func testStartHouseholdHubCalledTwiceIsANoOp() {
        MainViewControllerKt.startHouseholdHub()
        MainViewControllerKt.startHouseholdHub()

        XCTAssertTrue(true, "startHouseholdHub() completed twice without throwing or crashing.")
    }

    /// The host app's key window already has a root view controller from
    /// `WindowGroup { ContentView() }` → `ComposeView` → `MainViewControllerKt.MainViewController()`
    /// by the time this test attaches. If Koin failed to start, or `App(externalApps:)` crashed
    /// while composing, there would be nothing under that root view controller to find.
    func testHostWindowHasComposedContent() throws {
        let window = try XCTUnwrap(keyWindow(), "No key window found on the host app.")
        let root = try XCTUnwrap(window.rootViewController, "Host window has no root view controller.")

        pumpRunLoop()

        XCTAssertFalse(
            root.view.subviews.isEmpty,
            "Root view controller's view has no subviews — Compose does not appear to have "
                + "composed anything."
        )
        XCTAssertGreaterThan(root.view.bounds.width, 0, "Root view has zero width.")
        XCTAssertGreaterThan(root.view.bounds.height, 0, "Root view has zero height.")
    }

    /// A second, independently-built controller — as opposed to the one already on screen above —
    /// loads and lays out to a sensible size on its own, hosted in its own throwaway window.
    func testFreshlyBuiltMainViewControllerLoadsAndLaysOut() {
        let originalKeyWindow = keyWindow()
        let controller = MainViewControllerKt.MainViewController()
        let scratchWindow = UIWindow(frame: UIScreen.main.bounds)
        scratchWindow.rootViewController = controller
        scratchWindow.makeKeyAndVisible()
        defer {
            scratchWindow.isHidden = true
            originalKeyWindow?.makeKeyAndVisible()
        }

        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        pumpRunLoop()

        XCTAssertGreaterThan(controller.view.bounds.width, 0, "Freshly built controller has zero width.")
        XCTAssertGreaterThan(controller.view.bounds.height, 0, "Freshly built controller has zero height.")
        XCTAssertFalse(
            controller.view.subviews.isEmpty,
            "Freshly built MainViewController composed nothing."
        )
    }

    // MARK: - Helpers

    private func keyWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })
    }

    /// Compose does not necessarily have laid-out subviews the instant a test method starts;
    /// give the run loop a few short turns so pending display-link/layout work completes.
    private func pumpRunLoop(turns: Int = 5, eachFor interval: TimeInterval = 0.05) {
        for _ in 0..<turns {
            RunLoop.current.run(until: Date().addingTimeInterval(interval))
        }
    }
}
