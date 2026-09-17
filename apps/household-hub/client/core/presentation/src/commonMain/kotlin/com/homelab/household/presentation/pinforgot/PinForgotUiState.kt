package com.homelab.household.presentation.pinforgot

import com.homelab.household.domain.model.Member

/** Who can vouch for you. With nobody else here, the hub itself is the only way back. */
data class PinForgotUiState(
    val member: Member,
    val others: List<Member> = emptyList(),
    val status: PinForgotStatus = PinForgotStatus.Loading
)
