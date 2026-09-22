package com.homelab.household.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.model.User
import com.homelab.household.domain.usecase.GetCurrentUserUseCase
import com.homelab.household.domain.usecase.ListHouseholdMembersUseCase
import com.homelab.household.domain.util.runCatchingSafe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The default colour a member wears when the hub does not say. */
private const val DEFAULT_COLOUR = "#3C6E4E"

class MembersViewModel(
    private val listHouseholdMembers: ListHouseholdMembersUseCase,
    private val getCurrentUser: GetCurrentUserUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MembersUiState())
    val uiState: StateFlow<MembersUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Also the "Try again" when the hub didn't answer. */
    fun load() {
        _uiState.update { it.copy(status = MembersStatus.Loading) }
        viewModelScope.launch {
            runCatchingSafe {
                val you = getCurrentUser()
                val household = listHouseholdMembers()
                you to household
            }.fold(
                onSuccess = { (you, household) ->
                    _uiState.update {
                        it.copy(
                            rows = rows(you, household),
                            youAreAdmin =
                                you?.isAdmin == true,
                            status = MembersStatus.Ready,
                        )
                    }
                },
                onFailure = { error ->
                    val status = if (error is ServerOfflineException) MembersStatus.Unreachable else MembersStatus.Failed
                    _uiState.update { it.copy(status = status) }
                },
            )
        }
    }

    /** You first: it is your household seen from your side. */
    private fun rows(
        you: User?,
        household: List<User>,
    ): List<MemberRow> =
        household.sortedByDescending { it.id == you?.id }.map { member ->
            MemberRow(
                id = member.id,
                name = member.fullName,
                avatarColor = member.avatarColor ?: DEFAULT_COLOUR,
                isYou = member.id == you?.id,
                isAdmin = member.isAdmin,
            )
        }
}
