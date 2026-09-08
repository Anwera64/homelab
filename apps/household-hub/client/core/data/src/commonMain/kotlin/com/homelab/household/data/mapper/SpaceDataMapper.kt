package com.homelab.household.data.mapper

import com.homelab.household.data.dto.SpaceReadDto
import com.homelab.household.domain.model.Space
import com.homelab.household.domain.model.SpaceType

object SpaceDataMapper {
    fun toDomain(dto: SpaceReadDto): Space = Space(
        id = dto.id,
        name = dto.name,
        type = if (dto.type.equals("household", ignoreCase = true)) SpaceType.HOUSEHOLD else SpaceType.PERSONAL,
        ownerId = dto.owner_id,
        settings = dto.settings,
        createdAt = dto.created_at
    )
}
