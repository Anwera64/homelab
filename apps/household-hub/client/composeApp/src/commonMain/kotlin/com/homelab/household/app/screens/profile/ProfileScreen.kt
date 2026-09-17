package com.homelab.household.app.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.presentation.profile.ProfileEvent
import com.homelab.household.presentation.profile.ProfileViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Your own account: the members list, your PIN, and the two ways out. */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onMembers: () -> Unit,
    onChangePin: () -> Unit,
    onLeave: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            ProfileEvent.SignedOut -> onSignedOut()
        }
    }

    ProfileContent(
        state = state,
        onBack = onBack,
        onMembers = onMembers,
        onChangePin = onChangePin,
        onLeave = onLeave,
        onSignOut = viewModel::onSignOut,
        modifier = modifier
    )
}
