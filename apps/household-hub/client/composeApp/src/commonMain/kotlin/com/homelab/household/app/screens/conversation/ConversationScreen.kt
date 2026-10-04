package com.homelab.household.app.screens.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.result.ResultEffect
import com.homelab.household.app.navigation.CalendarConnected
import com.homelab.household.presentation.chatsession.ChatSessionViewModel
import com.homelab.household.presentation.profile.ProfileViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * A conversation, or a new one.
 *
 * The polling that recovers a dropped answer lives in the ViewModel's scope, so leaving the screen
 * ends it. Nothing is lost by that: the hub finishes the turn whether or not the phone is
 * listening, and reopening the conversation fetches whatever landed.
 *
 * Coming back to the app — unlocking the phone, most often — asks for the rest of any answer that
 * was left waiting, so it carries on from the last word rather than waiting out a retry.
 */
@Composable
fun ConversationScreen(
    sessionId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onConnectCalendar: () -> Unit = {},
) {
    val viewModel: ChatSessionViewModel = koinViewModel()
    val profileViewModel: ProfileViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by profileViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) { viewModel.open(sessionId) }
    // Back from connecting a calendar from a fix card: the hub has marked those steps fixed.
    ResultEffect<CalendarConnected> { viewModel.refresh() }
    LifecycleResumeEffect(viewModel) {
        viewModel.onForeground()
        onPauseOrDispose {}
    }

    ConversationContent(
        state = state,
        onComposerTextChange = viewModel::composerTextChanged,
        onSend = viewModel::sendMessage,
        onRetry = viewModel::sendMessage,
        onTryAgain = viewModel::regenerate,
        onBack = onBack,
        memberName = profile.member?.fullName.orEmpty(),
        onSelectAgent = viewModel::selectAgent,
        onRetryAgents = viewModel::retryAgents,
        onDecide = viewModel::decide,
        onConnectCalendar = onConnectCalendar,
        onApproveAutomatically = viewModel::approveAutomatically,
        onUndoAutomatic = viewModel::undoAutomatic,
        modifier = modifier,
    )
}
