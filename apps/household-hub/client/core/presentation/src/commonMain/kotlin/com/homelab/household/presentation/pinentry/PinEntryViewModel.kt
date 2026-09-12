package com.homelab.household.presentation.pinentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.usecase.LoginUseCase
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

class PinEntryViewModel(
    member: Member,
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinEntryUiState(member = member))
    val uiState: StateFlow<PinEntryUiState> = _uiState.asStateFlow()

    private val _events = Channel<PinEntryEvent>(Channel.BUFFERED)
    val events: Flow<PinEntryEvent> = _events.receiveAsFlow()

    private var pin = ""
    private var countdown: Job? = null

    /** The sixth digit sends the PIN; there is no submit button. */
    fun onDigit(digit: Char) {
        if (digit !in '0'..'9' || !acceptsInput() || pin.length == Pin.LENGTH) return
        pin += digit
        _uiState.update { it.copy(entered = pin.length) }
        if (pin.length == Pin.LENGTH) signIn()
    }

    fun onDelete() {
        if (!acceptsInput() || pin.isEmpty()) return
        pin = pin.dropLast(1)
        _uiState.update { it.copy(entered = pin.length) }
    }

    private fun acceptsInput(): Boolean = when (_uiState.value.status) {
        PinStatus.Checking, is PinStatus.Locked -> false
        else -> true
    }

    private fun signIn() {
        val attempt = pin
        _uiState.update { it.copy(status = PinStatus.Checking) }

        viewModelScope.launch {
            val result = runCatchingSafe { loginUseCase(_uiState.value.member.id, attempt) }
            if (result.isSuccess) {
                _events.send(PinEntryEvent.SignedIn)
                return@launch
            }

            pin = ""
            when (val error = result.exceptionOrNull()) {
                is PinLockedException -> countDown(error.retryAfterSeconds)
                is WrongPinException -> _uiState.update { it.copy(entered = 0, status = PinStatus.WrongPin(error.attemptsLeft)) }
                is ServerOfflineException -> _uiState.update { it.copy(entered = 0, status = PinStatus.Unreachable) }
                else -> _uiState.update { it.copy(entered = 0, status = PinStatus.Failed) }
            }
        }
    }

    private fun countDown(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (left in seconds downTo 1) {
                _uiState.update { it.copy(entered = 0, status = PinStatus.Locked(secondsLeft = left)) }
                delay(1_000)
            }
            _uiState.update { it.copy(status = PinStatus.Idle) }
        }
    }
}
