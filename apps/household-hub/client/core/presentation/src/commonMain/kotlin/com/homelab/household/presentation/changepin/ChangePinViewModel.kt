package com.homelab.household.presentation.changepin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.usecase.ChangePinUseCase
import com.homelab.household.domain.util.runCatchingSafe
import com.homelab.household.presentation.firstrun.PinError
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

class ChangePinViewModel(
    private val changePin: ChangePinUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePinUiState())
    val uiState: StateFlow<ChangePinUiState> = _uiState.asStateFlow()

    private val _events = Channel<ChangePinEvent>(Channel.BUFFERED)
    val events: Flow<ChangePinEvent> = _events.receiveAsFlow()

    private var countdown: Job? = null

    fun onCurrentChange(pin: String) {
        _uiState.update { it.copy(current = digits(pin), currentError = null) }
    }

    fun onNewChange(pin: String) {
        _uiState.update { it.copy(new = digits(pin), newError = null, againMismatch = false) }
    }

    fun onAgainChange(pin: String) {
        _uiState.update { it.copy(again = digits(pin), againMismatch = false) }
    }

    /** The primary button. It is never disabled, so tapping it early says what is missing. */
    fun change() {
        val state = _uiState.value
        when (state.status) {
            is ChangePinStatus.Saving, is ChangePinStatus.Locked -> return
            else -> Unit
        }

        val currentError = if (Pin.isValid(state.current)) null else CurrentPinError.NotSixDigits
        val newError = if (Pin.isValid(state.new)) null else PinError.NotSixDigits
        val mismatch = newError == null && state.new != state.again
        if (currentError != null || newError != null || mismatch) {
            _uiState.update { it.copy(currentError = currentError, newError = newError, againMismatch = mismatch) }
            return
        }

        _uiState.update { it.copy(status = ChangePinStatus.Saving) }
        viewModelScope.launch {
            runCatchingSafe { changePin(state.current, state.new) }.fold(
                onSuccess = {
                    _uiState.update { it.copy(status = ChangePinStatus.Idle) }
                    _events.send(ChangePinEvent.Changed)
                },
                onFailure = { error ->
                    when (error) {
                        is PinLockedException -> countDown(error.retryAfterSeconds)
                        is WrongPinException -> _uiState.update {
                            it.copy(
                                current = "",
                                currentError = CurrentPinError.Wrong(error.attemptsLeft),
                                status = ChangePinStatus.Idle
                            )
                        }
                        is ServerOfflineException -> _uiState.update { it.copy(status = ChangePinStatus.Unreachable) }
                        else -> _uiState.update { it.copy(status = ChangePinStatus.Failed) }
                    }
                }
            )
        }
    }

    private fun digits(typed: String) = typed.filter { it in '0'..'9' }.take(Pin.LENGTH)

    private fun countDown(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (left in seconds downTo 1) {
                _uiState.update { it.copy(current = "", status = ChangePinStatus.Locked(secondsLeft = left)) }
                delay(1_000)
            }
            _uiState.update { it.copy(status = ChangePinStatus.Idle) }
        }
    }
}
