package com.homelab.household.app.screens.whenagentsact

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.presentation.whenagentsact.WhenAgentsActViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Which writes agents do for you without asking. A switch here and an approval card's box are one setting. */
@Composable
fun WhenAgentsActScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WhenAgentsActViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WhenAgentsActContent(
        state = state,
        onBack = onBack,
        onToggle = viewModel::onToggle,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}
