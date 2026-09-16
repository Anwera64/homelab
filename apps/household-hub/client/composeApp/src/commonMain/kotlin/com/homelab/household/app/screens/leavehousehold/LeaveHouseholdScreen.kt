package com.homelab.household.app.screens.leavehousehold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.presentation.leavehousehold.LeaveHouseholdEvent
import com.homelab.household.presentation.leavehousehold.LeaveHouseholdViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Leaving the household for good. */
@Composable
fun LeaveHouseholdScreen(
    onBack: () -> Unit,
    onLeft: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: LeaveHouseholdViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            LeaveHouseholdEvent.Left -> onLeft()
        }
    }

    LeaveHouseholdContent(
        state = state,
        onPinChange = viewModel::onPinChange,
        onLeave = viewModel::leave,
        onBack = onBack,
        modifier = modifier
    )
}
