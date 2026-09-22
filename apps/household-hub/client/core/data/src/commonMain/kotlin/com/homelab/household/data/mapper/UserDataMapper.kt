package com.homelab.household.data.mapper

import com.homelab.household.data.dto.MemberProfileDto
import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.domain.model.Member
import com.homelab.household.domain.model.User

object UserDataMapper {
    fun toDomain(dto: UserReadDto): User =
        User(
            id = dto.id,
            fullName = dto.full_name,
            isAdmin = dto.is_admin,
            isActive = dto.is_active,
            personalSpaceId = dto.personal_space_id,
            avatarColor = dto.avatar_color,
            createdAt = dto.created_at,
        )

    fun toMember(dto: MemberProfileDto): Member =
        Member(
            id = dto.id,
            name = dto.full_name,
            avatarColor = dto.avatar_color,
        )
}
