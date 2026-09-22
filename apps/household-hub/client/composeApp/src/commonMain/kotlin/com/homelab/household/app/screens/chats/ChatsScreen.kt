package com.homelab.household.app.screens.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.presentation.chats.ChatsViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Every conversation, and the search over them. */
@Composable
fun ChatsScreen(
    onOpen: (String) -> Unit,
    onNewChat: () -> Unit,
    tabs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChatsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Reloaded on every arrival rather than once: a conversation you just had is the one you are
    // most likely to be coming back to look for.
    LaunchedEffect(Unit) { viewModel.load() }

    ChatsContent(
        state = state,
        onSearch = viewModel::search,
        onCancelSearch = viewModel::cancelSearch,
        onOpen = onOpen,
        onNewChat = onNewChat,
        onRetry = viewModel::load,
        tabs = tabs,
        modifier = modifier,
    )
}
