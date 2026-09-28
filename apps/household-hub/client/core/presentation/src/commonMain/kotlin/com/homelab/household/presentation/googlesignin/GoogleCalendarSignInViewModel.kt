package com.homelab.household.presentation.googlesignin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.GoogleSignInUnavailableException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.CalendarSignInFailure
import com.homelab.household.domain.model.CalendarSignInResult
import com.homelab.household.domain.usecase.StartGoogleCalendarSignInUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Google refuses passwords, so its calendar is connected by signing in. The hub builds Google's page
 * and keeps the sign-in; this opens the page and reads where the browser comes back to.
 */
class GoogleCalendarSignInViewModel(
    private val startSignIn: StartGoogleCalendarSignInUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GoogleSignInUiState())
    val uiState: StateFlow<GoogleSignInUiState> = _uiState.asStateFlow()

    private val _events = Channel<GoogleSignInEvent>(Channel.BUFFERED)
    val events: Flow<GoogleSignInEvent> = _events.receiveAsFlow()

    fun signIn() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(status = GoogleSignInStatus.Starting) }
        viewModelScope.launch {
            runCatchingSafe { startSignIn() }.fold(
                onSuccess = { page ->
                    _uiState.update { it.copy(status = GoogleSignInStatus.InBrowser) }
                    _events.send(GoogleSignInEvent.OpenBrowser(page))
                },
                onFailure = { error -> _uiState.update { it.copy(status = failureOf(error)) } },
            )
        }
    }

    /** [address] is where the browser came back to, or null when it was closed before the hub answered. */
    fun onBrowserReturned(address: String?) {
        when (val result = CalendarSignInResult.fromCallback(address)) {
            CalendarSignInResult.Connected -> {
                _uiState.update { it.copy(status = GoogleSignInStatus.Idle) }
                viewModelScope.launch { _events.send(GoogleSignInEvent.Connected) }
            }

            CalendarSignInResult.Cancelled -> {
                _uiState.update { it.copy(status = GoogleSignInStatus.Idle) }
            }

            is CalendarSignInResult.Failed -> {
                _uiState.update { it.copy(status = GoogleSignInStatus.Failed(result.failure)) }
            }
        }
    }

    private fun failureOf(error: Throwable): GoogleSignInStatus =
        when (error) {
            is ServerOfflineException -> GoogleSignInStatus.HubUnreachable
            is GoogleSignInUnavailableException -> GoogleSignInStatus.Unavailable
            else -> GoogleSignInStatus.Failed(CalendarSignInFailure.FAILED)
        }
}
