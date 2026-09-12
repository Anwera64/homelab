package com.homelab.household.app.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.domain.model.AuthStatus
import com.homelab.household.domain.usecase.CheckAuthStatusUseCase
import com.homelab.household.domain.usecase.GetHubHostUseCase
import com.homelab.household.presentation.launch.LaunchViewModel
import org.koin.compose.KoinApplication
import org.koin.dsl.module

/**
 * Screens build their own ViewModels, so a screen test needs the graph they resolve from —
 * the real ViewModels and use cases over a faked hub.
 */
@Composable
fun TestApp(
    authStatus: () -> AuthStatus,
    content: @Composable () -> Unit
) {
    val viewModelStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    KoinApplication(application = {
        modules(
            module {
                single { FakeAuthRepository(status = authStatus) }
                factory { CheckAuthStatusUseCase(get<FakeAuthRepository>()) }
                factory { GetHubHostUseCase(get<FakeAuthRepository>()) }
                factory { LaunchViewModel(get(), get()) }
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
