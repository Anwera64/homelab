package com.homelab.household.app.screens.members

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.members.MembersViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Everyone who lives on this hub, and what you may do about them. */
@Composable
fun MembersScreen(
    onBack: () -> Unit,
    onInvite: () -> Unit,
    onResetPin: (Member) -> Unit,
    onRemove: (Member) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MembersViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MembersContent(
        state = state,
        onInvite = onInvite,
        onResetPin = { row -> onResetPin(Member(id = row.id, name = row.name, avatarColor = row.avatarColor)) },
        onRemove = { row -> onRemove(Member(id = row.id, name = row.name, avatarColor = row.avatarColor)) },
        onRetry = viewModel::load,
        onBack = onBack,
        modifier = modifier,
    )
}
