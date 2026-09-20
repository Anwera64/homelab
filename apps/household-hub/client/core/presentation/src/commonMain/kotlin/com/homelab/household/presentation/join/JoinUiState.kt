package com.homelab.household.presentation.join

import com.homelab.household.domain.model.InvitePreview
import com.homelab.household.presentation.firstrun.NameError
import com.homelab.household.presentation.firstrun.PinError

/**
 * The join form: who invited them, what they have typed, and the colours somebody here already
 * wears — colour is how the schedule tells two people apart, so a taken one cannot be chosen.
 */
data class JoinUiState(
    val preview: InvitePreview,
    val name: String = preview.invitedName,
    val pin: String = "",
    val colour: String = "",
    val takenColours: Set<String> = emptySet(),
    val nameError: NameError? = null,
    val pinError: PinError? = null,
    val status: JoinStatus = JoinStatus.Idle
)
