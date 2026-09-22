package com.homelab.household.app.screens.invitecreate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.presentation.invitecreate.InviteCreateViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Making the one-time code an admin reads out to whoever is joining. */
@Composable
fun InviteCreateScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: InviteCreateViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    InviteCreateContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onAdminChange = viewModel::onAdminChange,
        onCreate = viewModel::create,
        onNewCode = viewModel::newCode,
        // For a code that is easier sent over chat than read across a room.
        onCopy = { state.invite?.let { clipboard.setText(AnnotatedString(it.code)) } },
        onBack = onBack,
        modifier = modifier,
    )
}
