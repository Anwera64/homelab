package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Google's sign-in page, as the hub built it for this member. */
@Serializable
data class GoogleSignInStartDto(
    @SerialName("authorization_url") val authorizationUrl: String,
)
