package com.homelab.household.app.screens.memories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.presentation.memories.MemoriesViewModel
import org.koin.compose.viewmodel.koinViewModel

/** What the agents know: your memories and the household's. Opened from Profile's Memories row. */
@Composable
fun MemoriesScreen(
    onBack: () -> Unit,
    onChangesToCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MemoriesViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Coming back from Changes to check: the band recounts and the lists show what an answer changed.
    // The first showing is already loading, so only later ones refresh.
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        if (shown) viewModel.refresh()
        shown = true
    }

    MemoriesContent(
        state = state,
        onBack = onBack,
        onTab = viewModel::onTab,
        onChangesToCheck = onChangesToCheck,
        onRemove = viewModel::onRemove,
        onUndo = viewModel::onUndo,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}
