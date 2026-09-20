package com.homelab.household.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.ListHouseholdMembersUseCase
import com.homelab.household.domain.usecase.LogoutUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val listHouseholdMembers: ListHouseholdMembersUseCase,
    private val logout: LogoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    /** Also the "Try again" when the hub didn't answer. */
    fun load() {
        _uiState.update { it.copy(status = ProfileStatus.Loading) }
        viewModelScope.launch {
            runCatchingSafe {
                val member = getCurrentUser()
                val household = listHouseholdMembers()
                member to household
            }.fold(
                onSuccess = { (member, household) ->
                    // Nothing can promote anyone, so the only admin cannot leave (design notes §4).
                    val soleAdmin = member?.isAdmin == true && household.count { it.isAdmin } <= 1
                    _uiState.update {
                        it.copy(member = member, isSoleAdmin = soleAdmin, status = ProfileStatus.Ready)
                    }
                },
                onFailure = { error ->
                    val status = if (error is ServerOfflineException) ProfileStatus.Unreachable else ProfileStatus.Failed
                    _uiState.update { it.copy(status = status) }
                }
            )
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            logout()
            _events.send(ProfileEvent.SignedOut)
        }
    }
}
