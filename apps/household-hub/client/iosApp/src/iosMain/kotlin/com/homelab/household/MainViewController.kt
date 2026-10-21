package com.homelab.household

import androidx.compose.ui.window.ComposeUIViewController
import com.homelab.household.app.App
import com.homelab.household.data.network.HubConfig
import com.homelab.household.data.telemetry.TelemetryConfig
import com.homelab.household.sdk.HouseholdHubSdk
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi

/**
 * The iOS entry point, and the twin of `HouseholdHubApplication` + `MainActivity` on Android.
 *
 * `HouseholdHubApplication.onCreate` gets its "exactly once" from Android: the system creates one
 * Application per process. iOS hands us no such hook — Swift just calls a function — so the
 * guarantee is spelled out here instead: [koin] is a `lazy` whose initializer *is* the Koin start,
 * and `lazy`'s default mode is synchronized. Calling [startHouseholdHub] twice (or from two
 * threads) touches an already-initialized value and does nothing, so `startKoin`'s
 * `KoinAppAlreadyStartedException` is unreachable no matter what the Swift side does.
 *
 * The hub and log collector come from Info.plist (`HubBaseUrl`, `TelemetryBaseUrl`), which
 * project.yml fills per configuration: Debug is the dev app, Release the household's — the
 * counterpart of Android's per-build-type `BuildConfig.HUB_URL`. Debug logging follows
 * [Platform.isDebugBinary]. The SDK's own telemetry default is off, so that nothing but a real
 * app ever sends any.
 */
@OptIn(ExperimentalNativeApi::class)
private val koin by lazy {
    HouseholdHubSdk.init(
        hubConfig =
            HubConfig(
                baseUrl = infoString("HubBaseUrl"),
                isDebug = Platform.isDebugBinary,
            ),
        telemetryConfig =
            TelemetryConfig(
                endpoint = infoString("TelemetryBaseUrl"),
                appVersion =
                    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
                        ?: "unknown",
            ),
    )
}

/** An Info.plist value; a missing key is a broken build, so it fails at start rather than later. */
private fun infoString(key: String): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String
        ?: error("Info.plist has no $key: project.yml sets it per configuration")

/**
 * Starts the DI graph if it is not already running. Idempotent; safe from any thread.
 *
 * Exposed to Swift as `MainViewControllerKt.startHouseholdHub()` for a host that wants the graph up
 * before the first frame. [MainViewController] calls it too, so a host that only builds the view
 * controller is already covered.
 */
fun startHouseholdHub() {
    koin
}

/**
 * The Compose UI, wrapped in the `UIViewController` that SwiftUI hosts.
 *
 * PascalCase because `ContentView.swift` calls this symbol by name, as
 * `MainViewControllerKt.MainViewController()`. It is an ObjC entry point, not a Kotlin function.
 */
@Suppress("ktlint:standard:function-naming")
fun MainViewController(): UIViewController {
    startHouseholdHub()
    return ComposeUIViewController {
        App(externalApps = IosExternalApps(), dragHidesKeyboard = true)
    }
}
