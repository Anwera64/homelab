package com.homelab.household.presentation.calendarconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.CalendarRejectedException
import com.homelab.household.domain.exception.CalendarUnreachableException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.CalendarProvider
import com.homelab.household.domain.usecase.ConnectCalendarUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CalendarConnectViewModel(
    provider: CalendarProvider,
    private val connectCalendar: ConnectCalendarUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalendarConnectUiState(provider = provider))
    val uiState: StateFlow<CalendarConnectUiState> = _uiState.asStateFlow()

    private val _events = Channel<CalendarConnectEvent>(Channel.BUFFERED)
    val events: Flow<CalendarConnectEvent> = _events.receiveAsFlow()

    fun onAccountChange(account: String) {
        _uiState.update { it.copy(account = account, accountMissing = false) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, passwordMissing = false) }
    }

    fun onServerChange(server: String) {
        _uiState.update { it.copy(server = server, serverMissing = false) }
    }

    fun onCalendarNameChange(name: String) {
        _uiState.update { it.copy(calendarName = name) }
    }

    /**
     * The primary button. It is never disabled, so tapping it early says what is missing. A
     * failure keeps everything typed: the fix is usually one field, not all of them.
     */
    fun connect() {
        val state = _uiState.value
        if (state.status == CalendarConnectStatus.Checking) return

        val accountMissing = state.account.isBlank()
        val passwordMissing = state.password.isBlank()
        val serverMissing = state.presetServer == null && state.server.isBlank()
        if (accountMissing || passwordMissing || serverMissing) {
            _uiState.update {
                it.copy(
                    accountMissing = accountMissing,
                    passwordMissing = passwordMissing,
                    serverMissing = serverMissing,
                )
            }
            return
        }

        _uiState.update { it.copy(status = CalendarConnectStatus.Checking) }
        viewModelScope.launch {
            runCatchingSafe {
                connectCalendar(
                    provider = state.provider,
                    account = state.account,
                    password = state.password,
                    server = state.server,
                    calendarName = state.calendarName,
                )
            }.fold(
                onSuccess = {
                    _uiState.update { it.copy(status = CalendarConnectStatus.Idle) }
                    _events.send(CalendarConnectEvent.Connected)
                },
                onFailure = { error -> _uiState.update { it.copy(status = failureOf(error)) } },
            )
        }
    }

    private fun failureOf(error: Throwable): CalendarConnectStatus =
        when (error) {
            is CalendarRejectedException -> CalendarConnectStatus.Rejected
            is CalendarUnreachableException -> CalendarConnectStatus.CalendarUnreachable
            is ServerOfflineException -> CalendarConnectStatus.HubUnreachable
            else -> CalendarConnectStatus.Failed
        }
}
