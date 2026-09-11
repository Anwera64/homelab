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
 * [hubAddress] is passed in by the platform app module, so the UI never reaches into the data layer.
 */
@Composable
fun App(
    hubAddress: String,
    authViewModel: AuthViewModel = koinViewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()

    LaunchedEffect(authViewModel) { authViewModel.checkStatus() }

    HearthTheme {
        AppNavigation(
            hubStatus = uiState.hubStatus,
            hubAddress = hubAddress,
            onRetry = authViewModel::checkStatus
        )
    }
}
