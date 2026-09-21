package com.homelab.household.app.screens.pinforgot

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.homelab.household.domain.model.Member
import com.homelab.household.presentation.pinforgot.PinForgotStatus
import com.homelab.household.presentation.pinforgot.PinForgotUiState

/** The forgotten-PIN screen in each state worth drawing. `PinForgotScreenTest` renders them all. */
class PinForgotUiStateProvider : PreviewParameterProvider<PinForgotUiState> {

    private val emma = Member(id = "emma", name = "Emma", avatarColor = "#3C6E4E")
    private val liam = Member(id = "liam", name = "Liam", avatarColor = "#C05638")

    private val named = listOf(
        "Somebody to ask" to PinForgotUiState(member = emma, others = listOf(liam), status = PinForgotStatus.Ready),
        "Nobody else here" to PinForgotUiState(member = emma, others = emptyList(), status = PinForgotStatus.Ready),
        // Arriving does not know yet whether there is anybody here to ask.
        "Arriving" to PinForgotUiState(member = emma, others = emptyList(), status = PinForgotStatus.Loading),
        "Hub unreachable" to PinForgotUiState(member = emma, status = PinForgotStatus.Unreachable)
    )

    override val values: Sequence<PinForgotUiState> = named.map { it.second }.asSequence()

    override fun getDisplayName(index: Int): String = named[index].first
}
