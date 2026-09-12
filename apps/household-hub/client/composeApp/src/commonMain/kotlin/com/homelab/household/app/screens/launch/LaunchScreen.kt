package com.homelab.household.app.screens.launch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.presentation.launch.LaunchEvent
import com.homelab.household.presentation.launch.LaunchViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Launch asks the hub where it stands the moment it appears, and moves on once it knows.
 * An unreachable hub stays here with a "Try again".
 */
@Composable
fun LaunchScreen(
    onSignIn: () -> Unit,
    onFirstRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: LaunchViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            LaunchEvent.GoToSignIn -> onSignIn()
            LaunchEvent.GoToFirstRun -> onFirstRun()
        }
    }

    LaunchContent(
        state = state,
        onRetry = viewModel::checkHub,
        modifier = modifier
    )
}
