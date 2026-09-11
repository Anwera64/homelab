package com.homelab.household.data.local

import kotlinx.serialization.Serializable

/** What a persistent [TokenStorage] writes to disk. */
@Serializable
internal data class TokenDiskPayload(
    val accessToken: String,
    val refreshToken: String? = null
)
