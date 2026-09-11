package com.homelab.household.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.homelab.household.app.navigation.AppNavigation
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.presentation.viewmodel.AuthViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app shell: it owns the ViewModel and asks the hub where it stands on first composition.
 * The hub address is read directly from the ViewModel's UI state.
 */
@Composable
fun App(
    authViewModel: AuthViewModel = koinViewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()

    LaunchedEffect(authViewModel) { authViewModel.checkStatus() }

    HearthTheme {
        AppNavigation(
            hubStatus = uiState.hubStatus,
            hubAddress = uiState.hubAddress,
            onRetry = authViewModel::checkStatus
        )
    }
}
