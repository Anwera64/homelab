package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionSecretToggleDto(
    @SerialName("is_secret") val is_secret: Boolean
)
