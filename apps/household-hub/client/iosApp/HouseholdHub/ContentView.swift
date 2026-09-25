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
            // The Compose UI is edge to edge and pads every safe area itself — the status bar,
            // the home indicator and the keyboard, all through `WindowInsets.safeDrawing` (the
            // keyboard part is what `android:windowSoftInputMode="adjustResize"` buys on the other
            // platform). So SwiftUI hands it the whole window and stays out of all of them. Keeping
            // any region here would apply that inset twice: ignoring only the keyboard once left a
            // white strip of SwiftUI background above and below the app, plus Compose's own
            // padding on top of it (#45). `EdgeToEdgeHostingTests` pins this.
            .ignoresSafeArea()
    }
}
