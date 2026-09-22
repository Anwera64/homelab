package com.homelab.household.data.mapper

import com.homelab.household.data.dto.InvitePreviewReadDto
import com.homelab.household.data.dto.InviteReadDto
import com.homelab.household.domain.model.Invite
import com.homelab.household.domain.model.InvitePreview

object InviteDataMapper {
    fun toDomain(dto: InviteReadDto): Invite =
        Invite(
            code = dto.code,
            invitedName = dto.invited_name,
            isAdmin = dto.is_admin,
            expiresInSeconds = dto.expires_in_seconds,
        )

    fun toPreview(dto: InvitePreviewReadDto): InvitePreview =
        InvitePreview(
            invitedName = dto.invited_name,
            inviterName = dto.inviter_name,
            inviterAvatarColor = dto.inviter_avatar_color,
        )
}
