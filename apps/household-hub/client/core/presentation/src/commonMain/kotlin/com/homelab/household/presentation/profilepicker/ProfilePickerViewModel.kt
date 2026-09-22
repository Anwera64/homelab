package com.homelab.household.presentation.profilepicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.usecase.ListMembersUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfilePickerViewModel(
    private val listMembersUseCase: ListMembersUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfilePickerUiState())
    val uiState: StateFlow<ProfilePickerUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProfilePickerEvent>(Channel.BUFFERED)
    val events: Flow<ProfilePickerEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    /** Also the "Try again" when the hub didn't answer. */
    fun load() {
        _uiState.update { it.copy(status = PickerStatus.Loading) }

        viewModelScope.launch {
            val status =
                runCatchingSafe { listMembersUseCase() }
                    .fold(
                        onSuccess = { members -> PickerStatus.Loaded(members) },
                        onFailure = { error ->
                            if (error is ServerOfflineException) PickerStatus.Unreachable else PickerStatus.Failed
                        },
                    )
            _uiState.update { it.copy(status = status) }
        }
    }

    fun onSelectMember(member: Member) {
        viewModelScope.launch { _events.send(ProfilePickerEvent.GoToPin(member)) }
    }
}
