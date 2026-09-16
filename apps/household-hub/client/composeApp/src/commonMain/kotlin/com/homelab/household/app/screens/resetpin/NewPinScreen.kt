package com.homelab.household.app.screens.resetpin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.presentation.resetpin.NewPinEvent
import com.homelab.household.presentation.resetpin.NewPinViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Choosing the PIN a reset code sets, and being signed in with it. */
@Composable
fun NewPinScreen(
    code: String,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: NewPinViewModel = koinViewModel(parameters = { parametersOf(code) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            NewPinEvent.SignedIn -> onSignedIn()
        }
    }

    NewPinContent(
        state = state,
        onPinChange = viewModel::onPinChange,
        onAgainChange = viewModel::onAgainChange,
        onSetPin = viewModel::setPin,
        modifier = modifier
    )
}
