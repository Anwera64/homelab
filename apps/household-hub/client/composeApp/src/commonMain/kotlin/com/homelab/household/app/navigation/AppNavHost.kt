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
                WithEntryViewModels {
                    screens.Launch(
                        onSignIn = { backStack.startOver(Destination.SignIn) },
                        onFirstRun = { backStack.startOver(Destination.FirstRun) },
                        onSignedIn = { backStack.startOver(Destination.Home) }
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

/** Where the app starts. Hoisted so a test can watch where it goes. */
@Composable
fun rememberAppBackStack(): SnapshotStateList<NavKey> =
    remember { mutableStateListOf(Destination.Launch) }

/** Somewhere the user can't come back from: launch's answer, a finished form, a signed-in member. */
private fun SnapshotStateList<NavKey>.startOver(destination: Destination) {
    clear()
    add(destination)
}
