package com.homelab.household.presentation.pinforgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.usecase.ListMembersUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PinForgotViewModel(
    member: Member,
    private val listMembers: ListMembersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinForgotUiState(member = member))
    val uiState: StateFlow<PinForgotUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Also the "Try again" when the hub didn't answer. */
    fun load() {
        _uiState.update { it.copy(status = PinForgotStatus.Loading) }
        viewModelScope.launch {
            runCatchingSafe { listMembers() }.fold(
                onSuccess = { members ->
                    val others = members.filter { it.id != _uiState.value.member.id }
                    _uiState.update { it.copy(others = others, status = PinForgotStatus.Ready) }
                },
                onFailure = { error ->
                    val status = if (error is ServerOfflineException) {
                        PinForgotStatus.Unreachable
                    } else {
                        PinForgotStatus.Failed
                    }
                    _uiState.update { it.copy(status = status) }
                }
            )
        }
    }
}
