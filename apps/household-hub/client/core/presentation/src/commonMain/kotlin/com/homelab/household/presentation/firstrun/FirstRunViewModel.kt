package com.homelab.household.presentation.firstrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.HubAlreadySetUpException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.MemberName
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.usecase.FirstRunOnboardUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FirstRunViewModel(
    private val onboardUseCase: FirstRunOnboardUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirstRunUiState())
    val uiState: StateFlow<FirstRunUiState> = _uiState.asStateFlow()

    private val _events = Channel<FirstRunEvent>(Channel.BUFFERED)
    val events: Flow<FirstRunEvent> = _events.receiveAsFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onPinChange(pin: String) {
        _uiState.update { it.copy(pin = pin.filter { c -> c in '0'..'9' }.take(Pin.LENGTH), pinError = null) }
    }

    fun onColourSelect(colour: String) {
        _uiState.update { it.copy(colour = colour) }
    }

    /** The primary button. It is never disabled, so tapping it early says what's missing instead. */
    fun create() {
        val state = _uiState.value
        if (state.isCreating) return

        val nameError = when {
            state.name.isBlank() -> NameError.Missing
            !MemberName.isValid(state.name) -> NameError.TooLong
            else -> null
        }
        val pinError = if (Pin.isValid(state.pin)) null else PinError.NotSixDigits
        if (nameError != null || pinError != null) {
            _uiState.update { it.copy(nameError = nameError, pinError = pinError) }
            return
        }

        _uiState.update { it.copy(isCreating = true, failure = null) }
        viewModelScope.launch {
            val result = runCatchingSafe { onboardUseCase(state.name, state.pin, state.colour) }
            _uiState.update { it.copy(isCreating = false, failure = result.exceptionOrNull()?.let(::failureFor)) }
            if (result.isSuccess) _events.send(FirstRunEvent.GoToHome)
        }
    }

    private fun failureFor(error: Throwable): FirstRunFailure = when (error) {
        is ServerOfflineException -> FirstRunFailure.Unreachable
        is HubAlreadySetUpException -> FirstRunFailure.AlreadySetUp
        else -> FirstRunFailure.Unknown
    }
}
