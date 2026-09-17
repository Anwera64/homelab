package com.homelab.household.presentation.resetpin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.presentation.invitecode.CODE_LENGTH
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a code can be made of, matching the hub: no 0 or O, no 1, I or L. */
private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

/**
 * Typing a reset code. The hub is not asked here: a reset code is only ever checked together with
 * the new PIN it sets, so nothing is spent by looking at it.
 */
class ResetCodeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ResetCodeUiState())
    val uiState: StateFlow<ResetCodeUiState> = _uiState.asStateFlow()

    private val _events = Channel<ResetCodeEvent>(Channel.BUFFERED)
    val events: Flow<ResetCodeEvent> = _events.receiveAsFlow()

    fun onCodeChange(typed: String) {
        val code = typed.uppercase().filter { it in CODE_ALPHABET }.take(CODE_LENGTH)
        _uiState.update { it.copy(code = code, incomplete = false) }
    }

    /** The primary button. It is never disabled, so tapping it early says what is missing. */
    fun onContinue() {
        val code = _uiState.value.code
        if (code.length < CODE_LENGTH) {
            _uiState.update { it.copy(incomplete = true) }
            return
        }
        viewModelScope.launch { _events.send(ResetCodeEvent.GoToNewPin(code)) }
    }
}
