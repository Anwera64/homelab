package com.homelab.household.presentation.invitecode

import com.homelab.household.domain.model.InvitePreview

/** What happens once the hub recognises the code. */
sealed interface InviteCodeEvent {
    data class GoToJoin(val preview: InvitePreview, val code: String) : InviteCodeEvent
}
