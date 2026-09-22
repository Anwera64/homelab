package com.homelab.household.data.dto

import kotlinx.serialization.Serializable

/** The approver's own PIN, not the target's. */
@Serializable
data class PinResetApproveRequestDto(
    val pin: String,
)
