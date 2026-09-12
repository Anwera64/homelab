package com.homelab.household.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.homelab.household.app.screens.launch.LaunchScreen
import com.homelab.household.app.screens.placeholder.PlaceholderContent

/**
 * The root of the app: it owns the back stack and nothing else. Screens own their own state and
 * report back through the callbacks wired here, so navigation lives in exactly one place.
 */
@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val backStack = remember { mutableStateListOf<NavKey>(Destination.Launch) }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Destination.Launch> {
                LaunchScreen(
                    onSignIn = { backStack.startOver(Destination.SignIn) },
                    onFirstRun = { backStack.startOver(Destination.FirstRun) }
                )
            }
            entry<Destination.SignIn> {
                PlaceholderContent(
                    title = "Sign in",
                    detail = "The profile picker and PIN arrive in slice 1."
                )
            }
            entry<Destination.FirstRun> {
                PlaceholderContent(
                    title = "First run",
                    detail = "Setting up the first account arrives in slice 1."
                )
            }
        }
    )
}

/** Launch is answered once; there is nothing to come back to. */
private fun SnapshotStateList<NavKey>.startOver(destination: Destination) {
    clear()
    add(destination)
}
