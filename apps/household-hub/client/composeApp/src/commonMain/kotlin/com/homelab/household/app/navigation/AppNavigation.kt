package com.homelab.household.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.homelab.household.app.screens.launch.LaunchScreen
import com.homelab.household.presentation.viewmodel.auth.model.HubStatus

/** Slice 0 has one destination; slice 1 adds the onboarding branches behind it. */
data object Launch : NavKey

/**
 * Stateless: it renders the destination for the state it is given.
 * `App` owns the ViewModel and feeds it in.
 */
@Composable
fun AppNavigation(
    hubStatus: HubStatus,
    hubAddress: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backStack = remember { mutableStateListOf<NavKey>(Launch) }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Launch> {
                LaunchScreen(status = hubStatus, hubAddress = hubAddress, onRetry = onRetry)
            }
        }
    )
}
