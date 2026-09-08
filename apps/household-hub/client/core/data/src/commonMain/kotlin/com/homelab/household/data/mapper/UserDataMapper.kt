package com.homelab.household.data.mapper

import com.homelab.household.data.dto.UserReadDto
import com.homelab.household.domain.model.User

object UserDataMapper {
    fun toDomain(dto: UserReadDto): User = User(
        id = dto.id,
        username = dto.username,
        email = dto.email,
        fullName = dto.full_name,
        isAdmin = dto.is_admin,
        isActive = dto.is_active,
        personalSpaceId = dto.personal_space_id,
        avatarColor = dto.avatar_color,
        createdAt = dto.created_at
    )
}
