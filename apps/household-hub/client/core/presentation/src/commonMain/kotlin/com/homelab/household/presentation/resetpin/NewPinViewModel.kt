package com.homelab.household.presentation.resetpin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.CodeGuessesLockedException
import com.homelab.household.domain.exception.InviteInvalidException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.usecase.RedeemPinResetUseCase
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

class NewPinViewModel(
    private val code: String,
    private val redeemPinReset: RedeemPinResetUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewPinUiState())
    val uiState: StateFlow<NewPinUiState> = _uiState.asStateFlow()

    private val _events = Channel<NewPinEvent>(Channel.BUFFERED)
    val events: Flow<NewPinEvent> = _events.receiveAsFlow()

    private var countdown: Job? = null

    fun onPinChange(pin: String) {
        _uiState.update { it.copy(pin = digits(pin), pinError = null, againMismatch = false) }
    }

    fun onAgainChange(again: String) {
        _uiState.update { it.copy(again = digits(again), againMismatch = false) }
    }

    /** The primary button. It is never disabled, so tapping it early says what is missing. */
    fun setPin() {
        val state = _uiState.value
        when (state.status) {
            is NewPinStatus.Setting, is NewPinStatus.Locked -> return
            else -> Unit
        }

        val pinError = if (Pin.isValid(state.pin)) null else PinError.NotSixDigits
        val mismatch = pinError == null && state.pin != state.again
        if (pinError != null || mismatch) {
            _uiState.update { it.copy(pinError = pinError, againMismatch = mismatch) }
            return
        }

        _uiState.update { it.copy(status = NewPinStatus.Setting) }
        viewModelScope.launch {
            runCatchingSafe { redeemPinReset(code, state.pin) }.fold(
                onSuccess = {
                    _uiState.update { it.copy(status = NewPinStatus.Idle) }
                    _events.send(NewPinEvent.SignedIn)
                },
                onFailure = { error ->
                    when (error) {
                        is CodeGuessesLockedException -> countDown(error.retryAfterSeconds)
                        is InviteInvalidException -> _uiState.update { it.copy(status = NewPinStatus.Invalid) }
                        is ServerOfflineException -> _uiState.update { it.copy(status = NewPinStatus.Unreachable) }
                        else -> _uiState.update { it.copy(status = NewPinStatus.Failed) }
                    }
                },
            )
        }
    }

    private fun digits(typed: String) = typed.filter { it in '0'..'9' }.take(Pin.LENGTH)

    private fun countDown(seconds: Int) {
        countdown?.cancel()
        countdown =
            viewModelScope.launch {
                for (left in seconds downTo 1) {
                    _uiState.update { it.copy(status = NewPinStatus.Locked(secondsLeft = left)) }
                    delay(1_000)
                }
                _uiState.update { it.copy(status = NewPinStatus.Idle) }
            }
    }
}
