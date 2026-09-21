package com.homelab.household.app.screens.pinapprove

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.pinapprove.PinApproveStatus
import com.homelab.household.presentation.pinapprove.PinApproveUiState

/** Approving a reset, in each state worth drawing. `PinApproveScreenTest` renders them all. */
class PinApproveUiStateProvider : PreviewParameterProvider<PinApproveUiState> {

    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")
    private val base = PinApproveUiState(member = emma)

    private val named = listOf(
        "Asking for your PIN" to base,
        "PIN typed" to base.copy(pin = "246801"),
        "Checking your PIN" to base.copy(pin = "246801", status = PinApproveStatus.Checking),
        "Wrong PIN" to base.copy(status = PinApproveStatus.WrongPin(attemptsLeft = 3)),
        "Locked" to base.copy(status = PinApproveStatus.Locked(secondsLeft = 30)),
        "Code to read out" to base.copy(status = PinApproveStatus.Approved("P4XN7T"), secondsLeft = 892),
        "Hub unreachable" to base.copy(status = PinApproveStatus.Unreachable)
    )

    override val values: Sequence<PinApproveUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
