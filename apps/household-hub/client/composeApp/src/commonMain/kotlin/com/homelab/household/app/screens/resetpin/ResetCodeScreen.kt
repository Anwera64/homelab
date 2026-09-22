package com.homelab.household.app.screens.resetpin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.presentation.resetpin.ResetCodeEvent
import com.homelab.household.presentation.resetpin.ResetCodeViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Typing the reset code that lets someone choose a new PIN. */
@Composable
fun ResetCodeScreen(
    onBack: () -> Unit,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ResetCodeViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            is ResetCodeEvent.GoToNewPin -> onCode(event.code)
        }
    }

    ResetCodeContent(
        state = state,
        onCodeChange = viewModel::onCodeChange,
        onContinue = viewModel::onContinue,
        onBack = onBack,
        modifier = modifier,
    )
}
