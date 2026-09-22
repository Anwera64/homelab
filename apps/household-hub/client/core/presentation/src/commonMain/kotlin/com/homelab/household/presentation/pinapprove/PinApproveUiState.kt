package com.homelab.household.presentation.pinapprove

import com.homelab.household.domain.model.Member

/** Vouching for someone who forgot their PIN: your own PIN, then the code you read out to them. */
data class PinApproveUiState(
    val member: Member,
    val pin: String = "",
    val secondsLeft: Int = 0,
    val status: PinApproveStatus = PinApproveStatus.Idle,
)
