package com.homelab.household.app.screens.profilepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.profilepicker.ProfilePickerEvent
import com.homelab.household.presentation.profilepicker.ProfilePickerViewModel
import org.koin.compose.viewmodel.koinViewModel

/** "Who's here?": tap a face, then enter that member's PIN. Nothing to type an identifier for. */
@Composable
fun ProfilePickerScreen(
    onMemberSelected: (Member) -> Unit,
    onInviteCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ProfilePickerViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            is ProfilePickerEvent.GoToPin -> onMemberSelected(event.member)
        }
    }

    ProfilePickerContent(
        state = state,
        onMemberSelected = viewModel::onMemberSelected,
        onRetry = viewModel::load,
        onInviteCode = onInviteCode,
        modifier = modifier
    )
}
