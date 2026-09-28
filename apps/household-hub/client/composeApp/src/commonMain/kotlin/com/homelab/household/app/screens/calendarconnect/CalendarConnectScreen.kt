package com.homelab.household.app.screens.calendarconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.presentation.calendarconnect.CalendarConnectEvent
import com.homelab.household.presentation.calendarconnect.CalendarConnectViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The details one calendar provider needs, tried by the hub before anything is kept. */
@Composable
fun CalendarConnectScreen(
    provider: CalendarProvider,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CalendarConnectViewModel = koinViewModel(parameters = { parametersOf(provider) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            CalendarConnectEvent.Connected -> onConnect()
        }
    }

    CalendarConnectContent(
        state = state,
        onAccountChange = viewModel::onAccountChange,
        onPasswordChange = viewModel::onPasswordChange,
        onServerChange = viewModel::onServerChange,
        onCalendarNameChange = viewModel::onCalendarNameChange,
        onConnect = viewModel::connect,
        onOpenInstructions = { instructionsFor(provider)?.let(uriHandler::openUri) },
        onBack = onBack,
        modifier = modifier,
    )
}

/** Where each provider explains its app passwords. Other servers each have their own, so there is none. */
private fun instructionsFor(provider: CalendarProvider): String? =
    when (provider) {
        CalendarProvider.APPLE -> "https://support.apple.com/102654"
        CalendarProvider.GOOGLE, CalendarProvider.OTHER -> null
    }
