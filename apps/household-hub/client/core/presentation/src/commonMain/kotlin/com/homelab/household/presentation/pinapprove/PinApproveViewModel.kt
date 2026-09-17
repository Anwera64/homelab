package com.homelab.household.presentation.pinapprove

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.usecase.ApprovePinResetUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PinApproveViewModel(
    member: Member,
    private val approvePinReset: ApprovePinResetUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinApproveUiState(member = member))
    val uiState: StateFlow<PinApproveUiState> = _uiState.asStateFlow()

    private var countdown: Job? = null

    fun onPinChange(pin: String) {
        if (_uiState.value.status is PinApproveStatus.Locked) return
        _uiState.update { it.copy(pin = pin.filter { c -> c in '0'..'9' }.take(Pin.LENGTH)) }
    }

    /** The primary button. It is never disabled, so tapping it early says what is missing. */
    fun approve() {
        val state = _uiState.value
        when (state.status) {
            is PinApproveStatus.Checking, is PinApproveStatus.Locked, is PinApproveStatus.Approved -> return
            else -> Unit
        }
        if (!Pin.isValid(state.pin)) {
            _uiState.update { it.copy(status = PinApproveStatus.WrongPin(attemptsLeft = 0)) }
            return
        }

        _uiState.update { it.copy(status = PinApproveStatus.Checking) }
        viewModelScope.launch {
            runCatchingSafe { approvePinReset(state.member.id, state.pin) }.fold(
                onSuccess = { reset ->
                    _uiState.update { it.copy(pin = "", status = PinApproveStatus.Approved(reset.code)) }
                    countDown(reset.expiresInSeconds)
                },
                onFailure = { error ->
                    when (error) {
                        is PinLockedException -> lockFor(error.retryAfterSeconds)
                        is WrongPinException ->
                            _uiState.update { it.copy(pin = "", status = PinApproveStatus.WrongPin(error.attemptsLeft)) }
                        is ServerOfflineException -> _uiState.update { it.copy(status = PinApproveStatus.Unreachable) }
                        else -> _uiState.update { it.copy(status = PinApproveStatus.Failed) }
                    }
                }
            )
        }
    }

    private fun lockFor(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (left in seconds downTo 1) {
                _uiState.update { it.copy(pin = "", status = PinApproveStatus.Locked(secondsLeft = left)) }
                delay(1_000)
            }
            _uiState.update { it.copy(status = PinApproveStatus.Idle) }
        }
    }

    /** How long they have to use the code, counted down from what the hub said. */
    private fun countDown(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (left in seconds downTo 0) {
                _uiState.update { it.copy(secondsLeft = left) }
                delay(1_000)
            }
        }
    }
}
