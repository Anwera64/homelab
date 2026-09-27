package com.homelab.household.app.screens.calendarconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.household.app.platform.LocalExternalApps
import com.homelab.household.app.util.ObserveEvents
import com.homelab.household.domain.model.CalendarSignInResult
import com.homelab.household.presentation.googlesignin.GoogleCalendarSignInViewModel
import com.homelab.household.presentation.googlesignin.GoogleSignInEvent
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** Connecting a Google calendar by signing in with Google. The hub keeps the sign-in; this opens the browser. */
@Composable
fun GoogleCalendarSignInScreen(
    onBack: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GoogleCalendarSignInViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val externalApps = LocalExternalApps.current
    val scope = rememberCoroutineScope()

    ObserveEvents(viewModel.events) { event ->
        when (event) {
            is GoogleSignInEvent.OpenBrowser -> {
                scope.launch {
                    val returnedTo = externalApps.signInInBrowser(event.page, CalendarSignInResult.CALLBACK_SCHEME)
                    viewModel.onBrowserReturned(returnedTo)
                }
            }

            GoogleSignInEvent.Connected -> {
                onConnect()
            }
        }
    }

    GoogleCalendarSignInContent(
        state = state,
        onSignIn = viewModel::signIn,
        onBack = onBack,
        modifier = modifier,
    )
}
