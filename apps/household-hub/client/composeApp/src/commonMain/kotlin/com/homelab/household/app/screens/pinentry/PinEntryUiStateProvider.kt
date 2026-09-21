package com.homelab.household.app.screens.pinentry

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.pinentry.PinEntryUiState
import com.homelab.household.presentation.pinentry.PinStatus

/** The PIN pad in every state worth drawing. `PinEntryScreenTest` renders them all. */
class PinEntryUiStateProvider : PreviewParameterProvider<PinEntryUiState> {

    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")

    private val named = listOf(
        "Empty" to PinEntryUiState(member = emma),
        "Three digits in" to PinEntryUiState(member = emma, entered = 3),
        // Six digits are in and with the hub. The pad has no submit button, so the dots carry it.
        "Checking" to PinEntryUiState(member = emma, entered = 6, status = PinStatus.Checking),
        "Two tries from a wait" to PinEntryUiState(member = emma, status = PinStatus.WrongPin(attemptsLeft = 2)),
        "A miss" to PinEntryUiState(member = emma, status = PinStatus.WrongPin(attemptsLeft = 4)),
        "Locked" to PinEntryUiState(member = emma, status = PinStatus.Locked(secondsLeft = 30)),
        "Unreachable" to PinEntryUiState(member = emma, status = PinStatus.Unreachable)
    )

    override val values: Sequence<PinEntryUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
