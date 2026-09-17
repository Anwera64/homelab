package com.homelab.household.presentation.leavehousehold

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.SoleAdminException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.usecase.LeaveHouseholdUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LeaveHouseholdViewModel(
    private val leaveHousehold: LeaveHouseholdUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaveHouseholdUiState())
    val uiState: StateFlow<LeaveHouseholdUiState> = _uiState.asStateFlow()

    private val _events = Channel<LeaveHouseholdEvent>(Channel.BUFFERED)
    val events: Flow<LeaveHouseholdEvent> = _events.receiveAsFlow()

    private var countdown: Job? = null

    fun onPinChange(pin: String) {
        if (_uiState.value.status is LeaveHouseholdStatus.Locked) return
        _uiState.update { it.copy(pin = pin.filter { c -> c in '0'..'9' }.take(Pin.LENGTH)) }
    }

    /** The destructive button. It is never disabled, so tapping it early says what is missing. */
    fun leave() {
        val state = _uiState.value
        when (state.status) {
            is LeaveHouseholdStatus.Leaving, is LeaveHouseholdStatus.Locked -> return
            else -> Unit
        }
        if (!Pin.isValid(state.pin)) {
            _uiState.update { it.copy(status = LeaveHouseholdStatus.NotSixDigits) }
            return
        }

        _uiState.update { it.copy(status = LeaveHouseholdStatus.Leaving) }
        viewModelScope.launch {
            runCatchingSafe { leaveHousehold(state.pin) }.fold(
                onSuccess = {
                    _uiState.update { it.copy(status = LeaveHouseholdStatus.Idle) }
                    _events.send(LeaveHouseholdEvent.Left)
                },
                onFailure = { error ->
                    when (error) {
                        is PinLockedException -> countDown(error.retryAfterSeconds)
                        is WrongPinException -> _uiState.update {
                            it.copy(pin = "", status = LeaveHouseholdStatus.WrongPin(error.attemptsLeft))
                        }
                        is SoleAdminException -> _uiState.update { it.copy(status = LeaveHouseholdStatus.SoleAdmin) }
                        is ServerOfflineException ->
                            _uiState.update { it.copy(status = LeaveHouseholdStatus.Unreachable) }
                        else -> _uiState.update { it.copy(status = LeaveHouseholdStatus.Failed) }
                    }
                }
            )
        }
    }

    private fun countDown(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (left in seconds downTo 1) {
                _uiState.update { it.copy(pin = "", status = LeaveHouseholdStatus.Locked(secondsLeft = left)) }
                delay(1_000)
            }
            _uiState.update { it.copy(status = LeaveHouseholdStatus.Idle) }
        }
    }
}
