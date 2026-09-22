package com.homelab.household.app.screens.invitecode

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.presentation.invitecode.InviteCodeStatus
import com.homelab.household.presentation.invitecode.InviteCodeUiState

/** The code screen in each state worth drawing. `InviteCodeScreenTest` renders them all. */
class InviteCodeUiStateProvider : PreviewParameterProvider<InviteCodeUiState> {
    private val named =
        listOf(
            "Empty" to InviteCodeUiState(),
            "Part typed" to InviteCodeUiState(code = "K7M"),
            "Complete" to InviteCodeUiState(code = "K7M2QP"),
            "Checking with the hub" to InviteCodeUiState(code = "K7M2QP", status = InviteCodeStatus.Checking),
            "Too short" to InviteCodeUiState(code = "K7M", status = InviteCodeStatus.Incomplete),
            "Not a code the hub knows" to InviteCodeUiState(code = "ZZZZZZ", status = InviteCodeStatus.Invalid),
            "Guessing locked" to InviteCodeUiState(code = "ZZZZZZ", status = InviteCodeStatus.Locked(secondsLeft = 30)),
            "Hub unreachable" to InviteCodeUiState(code = "K7M2QP", status = InviteCodeStatus.Unreachable),
            "Hub answered badly" to InviteCodeUiState(code = "K7M2QP", status = InviteCodeStatus.Failed),
        )

    override val values: Sequence<InviteCodeUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
