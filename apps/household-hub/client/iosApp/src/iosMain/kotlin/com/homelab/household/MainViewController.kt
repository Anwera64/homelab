package com.homelab.household

import androidx.compose.ui.window.ComposeUIViewController
import com.homelab.household.app.App
import com.homelab.household.data.di.DEFAULT_BASE_URL
import com.homelab.household.data.network.HubConfig
import com.homelab.household.sdk.HouseholdHubSdk
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
 * The hub address comes from the default in [HouseholdHubSdk.init] — the same constant the
 * `BuildConfig.BASE_URL` on the Android side is generated from — with debug logging determined
 * by [Platform.isDebugBinary].
 */
@OptIn(ExperimentalNativeApi::class)
private val koin by lazy {
    HouseholdHubSdk.init(
        hubConfig =
            HubConfig(
                baseUrl = DEFAULT_BASE_URL,
                isDebug = Platform.isDebugBinary,
            ),
    )
}

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
        App(externalApps = IosExternalApps())
    }
}
