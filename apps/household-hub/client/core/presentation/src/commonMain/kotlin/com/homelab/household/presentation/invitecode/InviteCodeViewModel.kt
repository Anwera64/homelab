package com.homelab.household.presentation.invitecode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.CodeGuessesLockedException
import com.homelab.household.domain.exception.InviteInvalidException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.usecase.LookUpInviteUseCase
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

/** The characters a code can be made of, matching the hub: no 0 or O, no 1, I or L. */
private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

/** How long a code is. */
const val CODE_LENGTH = 6

class InviteCodeViewModel(
    private val lookUpInvite: LookUpInviteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(InviteCodeUiState())
    val uiState: StateFlow<InviteCodeUiState> = _uiState.asStateFlow()

    private val _events = Channel<InviteCodeEvent>(Channel.BUFFERED)
    val events: Flow<InviteCodeEvent> = _events.receiveAsFlow()

    private var countdown: Job? = null

    /** Whatever was typed or pasted, as the hub reads it. Nothing typed is refused, only ignored. */
    fun onCodeChange(typed: String) {
        if (_uiState.value.status is InviteCodeStatus.Locked) return
        val code = typed.uppercase().filter { it in CODE_ALPHABET }.take(CODE_LENGTH)
        _uiState.update { it.copy(code = code, status = InviteCodeStatus.Idle) }
    }

    /** The primary button. It is never disabled, so tapping it early says what is missing. */
    fun onContinue() {
        val state = _uiState.value
        when {
            state.status is InviteCodeStatus.Checking || state.status is InviteCodeStatus.Locked -> return
            state.code.length < CODE_LENGTH -> {
                _uiState.update { it.copy(status = InviteCodeStatus.Incomplete) }
                return
            }
        }

        _uiState.update { it.copy(status = InviteCodeStatus.Checking) }
        viewModelScope.launch {
            val result = runCatchingSafe { lookUpInvite(state.code) }
            result.fold(
                onSuccess = { preview ->
                    _uiState.update { it.copy(status = InviteCodeStatus.Idle) }
                    _events.send(InviteCodeEvent.GoToJoin(preview, state.code))
                },
                onFailure = { error ->
                    when (error) {
                        is CodeGuessesLockedException -> countDown(error.retryAfterSeconds)
                        is InviteInvalidException -> _uiState.update { it.copy(status = InviteCodeStatus.Invalid) }
                        is ServerOfflineException -> _uiState.update { it.copy(status = InviteCodeStatus.Unreachable) }
                        else -> _uiState.update { it.copy(status = InviteCodeStatus.Failed) }
                    }
                }
            )
        }
    }

    private fun countDown(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (left in seconds downTo 1) {
                _uiState.update { it.copy(status = InviteCodeStatus.Locked(secondsLeft = left)) }
                delay(1_000)
            }
            _uiState.update { it.copy(status = InviteCodeStatus.Idle) }
        }
    }
}
