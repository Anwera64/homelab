import SwiftUI
import UIKit
import HouseholdHubKit

/// Hosts the Compose `UIViewController` inside SwiftUI.
struct ComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {

    var body: some View {
        ComposeView()
            // Compose runs its own keyboard handling: it reads the keyboard inset itself and
            // resizes its content, which is what `android:windowSoftInputMode="adjustResize"`
            // buys on the other platform. Letting SwiftUI *also* inset the hosting view for the
            // keyboard would apply that shift twice and push the composer off-screen, so SwiftUI
            // is told to keep out of the keyboard's way — and only the keyboard's; the status bar
            // and home indicator insets still come through, and Compose lays out against them.
            .ignoresSafeArea(.keyboard)
    }
}
