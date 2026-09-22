package com.homelab.household.data.mapper

import com.homelab.household.data.dto.PinResetReadDto
import com.homelab.household.domain.model.ResetCode

object PinResetDataMapper {
    fun toDomain(dto: PinResetReadDto): ResetCode =
        ResetCode(
            code = dto.code,
            expiresInSeconds = dto.expires_in_seconds,
        )
}
