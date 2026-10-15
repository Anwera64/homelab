package com.homelab.household.app.screens.memories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.presentation.memories.MemoriesViewModel
import org.koin.compose.viewmodel.koinViewModel

/** What the agents know: your memories and the household's. Opened from Profile's Memories row. */
@Composable
fun MemoriesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MemoriesViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MemoriesContent(
        state = state,
        onBack = onBack,
        onTab = viewModel::onTab,
        onRemove = viewModel::onRemove,
        onUndo = viewModel::onUndo,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}
