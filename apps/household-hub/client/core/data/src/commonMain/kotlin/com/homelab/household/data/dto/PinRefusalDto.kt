package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Why a sign-in was refused: a 401 carries `attempts_left`, a 429 `retry_after_seconds`. */
@Serializable
data class PinRefusalDto(
    val detail: String? = null,
    @SerialName("attempts_left") val attempts_left: Int? = null,
    @SerialName("retry_after_seconds") val retry_after_seconds: Int? = null
)
