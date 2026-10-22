package com.homelab.household.app.screens.calendarchoose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.presentation.calendarchoose.CalendarChooseEvent
import com.homelab.household.presentation.calendarchoose.CalendarChooseViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Which of the account's calendars agents read: after connecting, and from the profile's calendar sheet. */
@Composable
fun CalendarChooseScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CalendarChooseViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            CalendarChooseEvent.Done -> onDone()
        }
    }

    CalendarChooseContent(
        state = state,
        onBack = onBack,
        onToggle = viewModel::onToggle,
        onDone = viewModel::onDone,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}
