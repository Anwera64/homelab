package com.homelab.household.app.screens.join

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.presentation.join.JoinEvent
import com.homelab.household.presentation.join.JoinViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Joining the household with a code that checked out. */
@Composable
fun JoinScreen(
    preview: InvitePreview,
    code: String,
    onJoined: () -> Unit,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: JoinViewModel = koinViewModel(parameters = { parametersOf(preview, code) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            JoinEvent.Joined -> onJoined()
        }
    }

    JoinContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onPinChange = viewModel::onPinChange,
        onColourSelect = viewModel::onColourSelect,
        onJoin = viewModel::join,
        modifier = modifier,
    )
}
