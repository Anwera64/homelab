package com.homelab.household.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

/**
 * The root of the app: it owns the back stack and nothing else. Screens own their own state and
 * report back through the callbacks wired here, so navigation lives in exactly one place.
 */
@Composable
fun AppNavHost(
    screens: AppScreens = RealAppScreens,
    backStack: SnapshotStateList<NavKey> = rememberAppBackStack(),
    modifier: Modifier = Modifier
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Destination.Launch> {
                screens.Launch(
                    onSignIn = { backStack.startOver(Destination.SignIn) },
                    onFirstRun = { backStack.startOver(Destination.FirstRun) },
                    onSignedIn = { backStack.startOver(Destination.Home) }
                )
            }
            entry<Destination.SignIn> { screens.SignIn() }
            entry<Destination.FirstRun> { screens.FirstRun() }
            entry<Destination.Home> { screens.Home() }
        }
    )
}

/** Where the app starts. Hoisted so a test can watch where it goes. */
@Composable
fun rememberAppBackStack(): SnapshotStateList<NavKey> =
    remember { mutableStateListOf(Destination.Launch) }

/** Launch is answered once; there is nothing to come back to. */
private fun SnapshotStateList<NavKey>.startOver(destination: Destination) {
    clear()
    add(destination)
}
