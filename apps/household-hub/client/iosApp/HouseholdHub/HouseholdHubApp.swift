import SwiftUI
import HouseholdHubKit

/// The SwiftUI shell. Everything visible is Compose; this exists to own the window.
///
/// `startHouseholdHub()` is the twin of `HouseholdHubApplication.onCreate` — Koin comes up before
/// the first view controller is built. It is idempotent on the Kotlin side, so calling it here and
/// again from `MainViewController()` starts nothing twice.
@main
struct HouseholdHubApp: App {

    init() {
        MainViewControllerKt.startHouseholdHub()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
