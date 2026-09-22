package com.homelab.household.app.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.homelab.household.app.platform.ExternalApps
import com.homelab.household.app.platform.LocalExternalApps
import com.homelab.household.data.datasource.local.InMemorySessionStorage
import com.homelab.household.data.datasource.local.StoredSessionLocalDataSource
import com.homelab.household.data.network.HubConfig
import com.homelab.household.sdk.sdkModules
import io.ktor.client.engine.HttpClientEngine
import org.koin.compose.KoinApplication
import org.koin.dsl.module

/** The hub the screens will name; `getHubHost()` strips the scheme off it. */
const val TEST_HUB_HOST = "hub.test.local"
const val TEST_HUB_URL = "https://$TEST_HUB_HOST"

/**
 * The app's real dependency graph with only its outermost seams replaced: the HTTP engine, where
 * tokens are kept, and the other apps a screen can hand the user to. Everything between a screen
 * and the network — ViewModels, use cases, repositories, mappers, the Ktor client and its auth —
 * is the production wiring.
 *
 * It takes a plain engine, so it knows nothing about any particular screen's hub.
 *
 * The one thing it does change about the app's own composition is the motion scale: it composes
 * [StillTheme], so the waiting patterns draw their resting frame. Tests that mount a screen's
 * `Content` directly compose [StillTheme] themselves — that is the whole of the off switch, and
 * `StillMotionInTestsTest` fails the build on a test that skips it.
 */
@Composable
fun TestApp(
    engine: HttpClientEngine,
    sessionStorage: StoredSessionLocalDataSource = InMemorySessionStorage(),
    externalApps: ExternalApps = FakeExternalApps(),
    content: @Composable () -> Unit,
) {
    val viewModelStoreOwner =
        remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }

    KoinApplication(application = {
        allowOverride(true)
        modules(
            sdkModules(HubConfig(TEST_HUB_URL)) +
                module {
                    single<HttpClientEngine> { engine }
                    single<StoredSessionLocalDataSource> { sessionStorage }
                },
        )
    }) {
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides viewModelStoreOwner,
            LocalExternalApps provides externalApps,
        ) {
            StillTheme {
                content()
            }
        }
    }
}

/** Counts the hand-offs to other apps instead of making them. */
class FakeExternalApps : ExternalApps {
    var tailscaleOpened = 0
        private set

    override fun openTailscale() {
        tailscaleOpened++
    }
}
