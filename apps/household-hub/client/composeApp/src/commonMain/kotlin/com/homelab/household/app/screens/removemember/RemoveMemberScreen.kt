package com.homelab.household.app.screens.removemember

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.removemember.RemoveMemberEvent
import com.homelab.household.presentation.removemember.RemoveMemberViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Removing someone from the household, their name typed out to mean it. */
@Composable
fun RemoveMemberScreen(
    member: Member,
    onBack: () -> Unit,
    onRemoved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: RemoveMemberViewModel = koinViewModel(parameters = { parametersOf(member) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            RemoveMemberEvent.Removed -> onRemoved()
        }
    }

    RemoveMemberContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onRemove = viewModel::remove,
        onBack = onBack,
        modifier = modifier
    )
}
