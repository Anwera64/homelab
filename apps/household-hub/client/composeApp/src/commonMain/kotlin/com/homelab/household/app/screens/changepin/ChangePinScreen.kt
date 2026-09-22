package com.homelab.household.app.screens.changepin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.presentation.changepin.ChangePinEvent
import com.homelab.household.presentation.changepin.ChangePinViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Changing your own PIN. Every other device signs out; this one keeps going. */
@Composable
fun ChangePinScreen(
    onBack: () -> Unit,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChangePinViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            ChangePinEvent.Changed -> onChange()
        }
    }

    ChangePinContent(
        state = state,
        onCurrentChange = viewModel::onCurrentChange,
        onNewChange = viewModel::onNewChange,
        onAgainChange = viewModel::onAgainChange,
        onChange = viewModel::change,
        onBack = onBack,
        modifier = modifier,
    )
}
