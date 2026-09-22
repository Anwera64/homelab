package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Why a request was refused. `code` names which refusal it was (`wrong_pin`, `pin_locked`,
 * `invite_invalid`, `name_taken`, `code_guesses_locked`, `sole_admin`); `attempts_left` comes with
 * a 401/403, `retry_after_seconds` with a 429.
 */
@Serializable
data class PinRefusalDto(
    val detail: String? = null,
    val code: String? = null,
    @SerialName("attempts_left") val attempts_left: Int? = null,
    @SerialName("retry_after_seconds") val retry_after_seconds: Int? = null,
)
