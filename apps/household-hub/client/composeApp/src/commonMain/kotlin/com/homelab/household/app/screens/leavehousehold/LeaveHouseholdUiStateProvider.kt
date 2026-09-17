package com.homelab.household.app.screens.leavehousehold

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.presentation.leavehousehold.LeaveHouseholdStatus
import com.homelab.household.presentation.leavehousehold.LeaveHouseholdUiState

/** Leaving, in each state worth drawing. `LeaveHouseholdScreenTest` renders them all. */
class LeaveHouseholdUiStateProvider : PreviewParameterProvider<LeaveHouseholdUiState> {

    private val named = listOf(
        "Asking for your PIN" to LeaveHouseholdUiState(),
        "PIN typed" to LeaveHouseholdUiState(pin = "135790"),
        "Wrong PIN" to LeaveHouseholdUiState(status = LeaveHouseholdStatus.WrongPin(attemptsLeft = 4)),
        "The only admin" to LeaveHouseholdUiState(pin = "135790", status = LeaveHouseholdStatus.SoleAdmin),
        "Locked" to LeaveHouseholdUiState(status = LeaveHouseholdStatus.Locked(secondsLeft = 30)),
        "Hub unreachable" to LeaveHouseholdUiState(pin = "135790", status = LeaveHouseholdStatus.Unreachable)
    )

    override val values: Sequence<LeaveHouseholdUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
