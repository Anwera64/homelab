package com.homelab.household.app.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.data.local.InMemoryTokenStorage
import com.homelab.household.data.local.TokenStorage
import com.homelab.household.data.remote.HubConfig
import com.homelab.household.sdk.sdkModules
import io.ktor.client.engine.HttpClientEngine
import org.koin.compose.KoinApplication
import org.koin.dsl.module

/** The address the screens will show; `getHubHost()` strips the scheme off it. */
const val TEST_HUB_URL = "https://hub.test.local"

/**
 * The app's real dependency graph with only its two outermost seams replaced: the HTTP engine
 * and where tokens are kept. Everything between a screen and the network — ViewModels, use
 * cases, repositories, mappers, the Ktor client and its auth — is the production wiring.
 *
 * It takes a plain engine, so it knows nothing about any particular screen's hub.
 */
@Composable
fun TestApp(engine: HttpClientEngine, content: @Composable () -> Unit) {
    val viewModelStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    KoinApplication(application = {
        allowOverride(true)
        modules(
            sdkModules(HubConfig(TEST_HUB_URL)) + module {
                single<HttpClientEngine> { engine }
                single<TokenStorage> { InMemoryTokenStorage() }
            }
        )
    }) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
            HearthTheme(darkTheme = false) {
                content()
            }
        }
    }
}
