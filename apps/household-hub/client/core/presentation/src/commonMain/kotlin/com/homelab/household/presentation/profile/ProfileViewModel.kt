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

    /**
     * Also the "Try again" when the hub didn't answer.
     *
     * The two asks are separate because they can fail separately: who you are is kept on this
     * phone and answers offline, while the household has to come from the hub. Showing the member
     * without the household is the offline profile — a name, a colour, and a line saying the hub
     * could not be reached — so the failure of the second must not throw away the first.
     */
    fun load() {
        _uiState.update { it.copy(status = ProfileStatus.Loading) }
        viewModelScope.launch {
            val member = runCatchingSafe { getCurrentUser() }
                .onSuccess { _uiState.update { state -> state.copy(member = it) } }

            runCatchingSafe { listHouseholdMembers() }.fold(
                onSuccess = { household ->
                    // Nothing can promote anyone, so the only admin cannot leave (design notes §4).
                    val soleAdmin = member.getOrNull()?.isAdmin == true && household.count { it.isAdmin } <= 1
                    _uiState.update { it.copy(isSoleAdmin = soleAdmin, status = ProfileStatus.Ready) }
                },
                onFailure = { error -> _uiState.update { it.copy(status = failureOf(error)) } }
            )

            // A member this phone could not name is a failure of its own, whatever the household did.
            member.onFailure { error -> _uiState.update { it.copy(status = failureOf(error)) } }
        }
    }

    private fun failureOf(error: Throwable): ProfileStatus =
        if (error is ServerOfflineException) ProfileStatus.Unreachable else ProfileStatus.Failed

    fun onSignOut() {
        viewModelScope.launch {
            logout()
            _events.send(ProfileEvent.SignedOut)
        }
    }
}
