package com.homelab.household.presentation.pinentry

import com.homelab.household.domain.model.Member

/**
 * Everything the PIN pad draws. Only how many digits are in is exposed, never the digits: the
 * screen shows dots.
 */
data class PinEntryUiState(
    val member: Member,
    val entered: Int = 0,
    val status: PinStatus = PinStatus.Idle
)
