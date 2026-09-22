package com.homelab.household.app.screens.pinentry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.pinentry.PinEntryEvent
import com.homelab.household.presentation.pinentry.PinEntryViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The PIN pad for the member tapped on "Who's here?". The sixth digit signs in. */
@Composable
fun PinEntryScreen(
    member: Member,
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PinEntryViewModel = koinViewModel(parameters = { parametersOf(member) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            PinEntryEvent.SignedIn -> onSignedIn()
        }
    }

    PinEntryContent(
        state = state,
        onDigit = viewModel::onDigit,
        onDelete = viewModel::onDelete,
        onBack = onBack,
        onForget = onForget,
        modifier = modifier,
    )
}
