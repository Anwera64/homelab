package com.homelab.household.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.homelab.household.domain.usecase.HasStoredSessionUseCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The root of the app: it owns the back stack and nothing else. Screens own their own state and
 * report back through the callbacks wired here, so navigation lives in exactly one place.
 */
@Composable
fun AppNavHost(
    screens: AppScreens = RealAppScreens,
    backStack: SnapshotStateList<NavKey> = rememberAppBackStack(),
    session: AppSession = rememberAppSession(),
    modifier: Modifier = Modifier
) {
    LaunchedEffect(session) {
        // Listening before renewing, so a token the hub refuses on the way sends the phone back too.
        launch(start = CoroutineStart.UNDISPATCHED) {
            session.signedOut.collect { backStack.startOver(Destination.SignIn) }
        }
        session.renew()
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Destination.Launch> {
                WithEntryViewModels {
                    screens.Launch(
                        onSignIn = { backStack.startOver(Destination.SignIn) },
                        onFirstRun = { backStack.startOver(Destination.FirstRun) }
                    )
                }
            }
            entry<Destination.SignIn> {
                WithEntryViewModels {
                    // The picker stays under the PIN pad, so back from a PIN returns to it.
                    screens.SignIn(onMemberSelected = { member -> backStack.add(Destination.Pin(member)) })
                }
            }
            entry<Destination.Pin> { destination ->
                WithEntryViewModels {
                    screens.Pin(
                        member = destination.member,
                        onSignedIn = { backStack.startOver(Destination.Home) },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
            }
            entry<Destination.FirstRun> {
                WithEntryViewModels {
                    screens.FirstRun(
                        onCreated = { backStack.startOver(Destination.Home) },
                        onSignIn = { backStack.startOver(Destination.SignIn) }
                    )
                }
            }
            entry<Destination.Home> {
                WithEntryViewModels { screens.Home() }
            }
        }
    )
}

/**
 * Where the app starts, decided on the phone: home for a member still signed in here, launch for
 * everyone else. Waits for the hub on nothing — a token it no longer accepts signs the phone out
 * when the session is renewed, or on the next call. Hoisted so a test can watch where it goes.
 */
@Composable
fun rememberAppBackStack(): SnapshotStateList<NavKey> {
    val hasStoredSession = koinInject<HasStoredSessionUseCase>()
    return remember {
        mutableStateListOf(if (hasStoredSession()) Destination.Home else Destination.Launch)
    }
}

/** Somewhere the user can't come back from: launch's answer, a finished form, a signed-in member. */
private fun SnapshotStateList<NavKey>.startOver(destination: Destination) {
    clear()
    add(destination)
}
