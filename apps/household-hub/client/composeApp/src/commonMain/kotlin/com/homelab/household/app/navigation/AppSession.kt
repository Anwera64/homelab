package com.homelab.household.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.homelab.household.domain.usecase.ObserveSignedOutUseCase
import com.homelab.household.domain.usecase.RenewSessionUseCase
import kotlinx.coroutines.flow.Flow
import org.koin.compose.koinInject

/**
 * What navigation needs from the session on this phone: when the hub stops accepting its token, and
 * a way to keep that token fresh. Behind a seam so the navigation tests can sign a phone out by hand.
 */
interface AppSession {
    val signedOut: Flow<Unit>

    suspend fun renew()
}

@Composable
fun rememberAppSession(): AppSession {
    val observeSignedOut = koinInject<ObserveSignedOutUseCase>()
    val renewSession = koinInject<RenewSessionUseCase>()
    return remember(observeSignedOut, renewSession) {
        object : AppSession {
            override val signedOut: Flow<Unit> = observeSignedOut()

            override suspend fun renew() = renewSession()
        }
    }
}
