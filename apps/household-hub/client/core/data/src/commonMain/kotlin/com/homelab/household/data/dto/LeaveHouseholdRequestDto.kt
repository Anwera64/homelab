package com.homelab.household.data.dto

import kotlinx.serialization.Serializable

/** The leaving member's own PIN. */
@Serializable
data class LeaveHouseholdRequestDto(val pin: String)
