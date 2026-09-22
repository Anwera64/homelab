package com.homelab.household.app.screens.firstrun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.presentation.firstrun.FirstRunEvent
import com.homelab.household.presentation.firstrun.FirstRunViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The first thing a new hub does: the first member names themselves, picks a PIN and a colour,
 * and becomes the admin. A hub someone else set up in the meantime offers sign-in instead.
 */
@Composable
fun FirstRunScreen(
    onCreated: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FirstRunViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            FirstRunEvent.GoToHome -> onCreated()
        }
    }

    FirstRunContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onPinChange = viewModel::onPinChange,
        onColourSelect = viewModel::onColourSelect,
        onCreate = viewModel::create,
        onSignIn = onSignIn,
        modifier = modifier,
    )
}
