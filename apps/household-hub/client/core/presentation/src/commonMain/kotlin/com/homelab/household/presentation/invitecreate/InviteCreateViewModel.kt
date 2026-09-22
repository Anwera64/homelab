package com.homelab.household.presentation.invitecreate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.NameTakenException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.MemberName
import com.homelab.household.domain.usecase.CreateInviteUseCase
import com.homelab.household.domain.util.runCatchingSafe
import com.homelab.household.presentation.firstrun.NameError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InviteCreateViewModel(
    private val createInvite: CreateInviteUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InviteCreateUiState())
    val uiState: StateFlow<InviteCreateUiState> = _uiState.asStateFlow()

    private var countdown: Job? = null

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onAdminChange(isAdmin: Boolean) {
        _uiState.update { it.copy(isAdmin = isAdmin) }
    }

    /** The primary button. It is never disabled, so tapping it early says what is missing. */
    fun create() {
        val state = _uiState.value
        if (state.status is InviteCreateStatus.Creating) return

        val nameError =
            when {
                state.name.isBlank() -> NameError.Missing
                !MemberName.isValid(state.name) -> NameError.TooLong
                else -> null
            }
        if (nameError != null) {
            _uiState.update { it.copy(nameError = nameError) }
            return
        }

        _uiState.update { it.copy(status = InviteCreateStatus.Creating) }
        viewModelScope.launch {
            runCatchingSafe { createInvite(state.name, state.isAdmin) }.fold(
                onSuccess = { invite ->
                    _uiState.update { it.copy(invite = invite, status = InviteCreateStatus.Idle) }
                    countDown(invite.expiresInSeconds)
                },
                onFailure = { error ->
                    when (error) {
                        is NameTakenException -> {
                            _uiState.update { it.copy(status = InviteCreateStatus.Idle, nameError = NameError.Taken) }
                        }

                        is ServerOfflineException -> {
                            _uiState.update { it.copy(status = InviteCreateStatus.Unreachable) }
                        }

                        else -> {
                            _uiState.update { it.copy(status = InviteCreateStatus.Failed) }
                        }
                    }
                },
            )
        }
    }

    /** "New code": the one on screen has been read out to nobody, or has run out. */
    fun newCode() {
        _uiState.update { it.copy(invite = null, secondsLeft = 0, status = InviteCreateStatus.Idle) }
        create()
    }

    /**
     * The hub says how many seconds are left rather than when it expires: this phone's clock may
     * not agree with the hub's, and a countdown that disagrees between two phones is a support call.
     */
    private fun countDown(seconds: Int) {
        countdown?.cancel()
        countdown =
            viewModelScope.launch {
                for (left in seconds downTo 1) {
                    _uiState.update { it.copy(secondsLeft = left) }
                    delay(1_000)
                }
                _uiState.update { it.copy(secondsLeft = 0, status = InviteCreateStatus.Expired) }
            }
    }
}
