package com.homelab.household.app.screens.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.presentation.chatsession.ChatSessionViewModel
import com.homelab.household.presentation.profile.ProfileViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * A conversation, or a new one.
 *
 * The polling that recovers a dropped answer lives in the ViewModel's scope, so leaving the screen
 * ends it. Nothing is lost by that: the hub finishes the turn whether or not the phone is
 * listening, and reopening the conversation fetches whatever landed.
 */
@Composable
fun ConversationScreen(
    sessionId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChatSessionViewModel = koinViewModel()
    val profileViewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by profileViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) { viewModel.open(sessionId) }

    ConversationContent(
        state = state,
        onComposerTextChange = viewModel::composerTextChanged,
        onSend = viewModel::sendMessage,
        onRetry = viewModel::sendMessage,
        onTryAgain = viewModel::regenerate,
        onBack = onBack,
        memberName = profile.member?.fullName.orEmpty(),
        modifier = modifier,
    )
}
