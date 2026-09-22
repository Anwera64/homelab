package com.homelab.household.presentation.removemember

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.usecase.RemoveMemberUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RemoveMemberViewModel(
    member: Member,
    private val removeMember: RemoveMemberUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RemoveMemberUiState(member = member))
    val uiState: StateFlow<RemoveMemberUiState> = _uiState.asStateFlow()

    private val _events = Channel<RemoveMemberEvent>(Channel.BUFFERED)
    val events: Flow<RemoveMemberEvent> = _events.receiveAsFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(typedName = name, nameMismatch = false) }
    }

    /** The primary button. It is never disabled, so tapping it early says what is missing. */
    fun remove() {
        val state = _uiState.value
        if (state.status is RemoveMemberStatus.Removing) return

        if (!state.typedName.trim().equals(state.member.name, ignoreCase = true)) {
            _uiState.update { it.copy(nameMismatch = true) }
            return
        }

        _uiState.update { it.copy(status = RemoveMemberStatus.Removing) }
        viewModelScope.launch {
            runCatchingSafe { removeMember(state.member.id) }.fold(
                onSuccess = {
                    _uiState.update { it.copy(status = RemoveMemberStatus.Idle) }
                    _events.send(RemoveMemberEvent.Removed)
                },
                onFailure = { error ->
                    val status =
                        if (error is ServerOfflineException) {
                            RemoveMemberStatus.Unreachable
                        } else {
                            RemoveMemberStatus.Failed
                        }
                    _uiState.update { it.copy(status = status) }
                },
            )
        }
    }
}
