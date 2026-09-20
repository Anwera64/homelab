package com.homelab.household.presentation.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.InviteInvalidException
import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.domain.model.MemberName
import com.homelab.household.domain.model.Pin
import com.homelab.household.domain.usecase.JoinHouseholdUseCase
import com.homelab.household.domain.usecase.ListMembersUseCase
import com.homelab.household.domain.util.runCatchingSafe
import com.homelab.household.presentation.firstrun.AvatarPalette
import com.homelab.household.presentation.firstrun.NameError
import com.homelab.household.presentation.firstrun.PinError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class JoinViewModel(
    preview: InvitePreview,
    private val code: String,
    private val joinHousehold: JoinHouseholdUseCase,
    private val listMembers: ListMembersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        JoinUiState(preview = preview, colour = AvatarPalette.swatches.first())
    )
    val uiState: StateFlow<JoinUiState> = _uiState.asStateFlow()

    private val _events = Channel<JoinEvent>(Channel.BUFFERED)
    val events: Flow<JoinEvent> = _events.receiveAsFlow()

    init {
        loadTakenColours()
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onPinChange(pin: String) {
        _uiState.update { it.copy(pin = pin.filter { c -> c in '0'..'9' }.take(Pin.LENGTH), pinError = null) }
    }

    fun onColourSelect(colour: String) {
        if (colour in _uiState.value.takenColours) return
        _uiState.update { it.copy(colour = colour) }
    }

    /** The primary button. It is never disabled, so tapping it early says what is missing. */
    fun join() {
        val state = _uiState.value
        if (state.status is JoinStatus.Joining) return

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

        _uiState.update { it.copy(status = JoinStatus.Joining) }
        viewModelScope.launch {
            val result = runCatchingSafe { joinHousehold(code, state.name, state.pin, state.colour) }
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(status = JoinStatus.Idle) }
                    _events.send(JoinEvent.Joined)
                },
                onFailure = { error ->
                    when (error) {
                        is NameTakenException ->
                            _uiState.update { it.copy(status = JoinStatus.Idle, nameError = NameError.Taken) }
                        is InviteInvalidException -> _uiState.update { it.copy(status = JoinStatus.Expired) }
                        is ServerOfflineException -> _uiState.update { it.copy(status = JoinStatus.Unreachable) }
                        else -> _uiState.update { it.copy(status = JoinStatus.Failed) }
                    }
                }
            )
        }
    }

    /** Colours somebody here already wears. An unreachable hub just means none are known yet. */
    private fun loadTakenColours() {
        viewModelScope.launch {
            val taken = runCatchingSafe { listMembers() }.getOrNull()?.map { it.avatarColor }?.toSet() ?: emptySet()
            _uiState.update { state ->
                val free = AvatarPalette.swatches.firstOrNull { it !in taken } ?: state.colour
                state.copy(takenColours = taken, colour = if (state.colour in taken) free else state.colour)
            }
        }
    }
}
