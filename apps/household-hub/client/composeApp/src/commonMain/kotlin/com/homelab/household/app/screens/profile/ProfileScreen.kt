package com.homelab.household.app.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.presentation.profile.ProfileEvent
import com.homelab.household.presentation.profile.ProfileViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Your own account: your calendar, what agents may do, the members list, your PIN, and the two ways out. */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onMembers: () -> Unit,
    onChangePin: () -> Unit,
    onLeave: () -> Unit,
    onSignedOut: () -> Unit,
    onCalendar: () -> Unit,
    onCalendarSignInAgain: () -> Unit,
    onWhenAgentsAct: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Every time the profile is shown, not once: coming back from connecting a calendar shows it,
    // and coming back from "When agents act" counts what was just switched.
    LaunchedEffect(viewModel) {
        viewModel.loadCalendar()
        viewModel.loadAutomaticWrites()
    }

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
        onCalendar = onCalendar,
        onCalendarSignInAgain = onCalendarSignInAgain,
        onWhenAgentsAct = onWhenAgentsAct,
        modifier = modifier,
        onDisconnectCalendar = viewModel::onDisconnectCalendar,
    )
}
