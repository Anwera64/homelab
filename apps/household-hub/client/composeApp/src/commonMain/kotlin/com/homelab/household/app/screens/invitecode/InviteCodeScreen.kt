package com.homelab.household.app.screens.invitecode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.presentation.invitecode.InviteCodeEvent
import com.homelab.household.presentation.invitecode.InviteCodeViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Typing the code that gets someone into the household. */
@Composable
fun InviteCodeScreen(
    onBack: () -> Unit,
    onInvite: (InvitePreview, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: InviteCodeViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            is InviteCodeEvent.GoToJoin -> onInvite(event.preview, event.code)
        }
    }

    InviteCodeContent(
        state = state,
        onCodeChange = viewModel::onCodeChange,
        // Codes are read out or sent over chat; pasting one is the common case, not the odd one.
        onPaste = { viewModel.onCodeChange(clipboard.getText()?.text.orEmpty()) },
        onContinue = viewModel::onContinue,
        onBack = onBack,
        modifier = modifier
    )
}
