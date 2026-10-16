package com.homelab.household.app.screens.changes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.presentation.memories.ChangesToCheckViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Changes to check, opened from the band on the memory page. */
@Composable
fun ChangesToCheckScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChangesToCheckViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ChangesToCheckContent(
        state = state,
        onBack = onBack,
        onAnswer = viewModel::onAnswer,
        onUndo = viewModel::onUndo,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}
