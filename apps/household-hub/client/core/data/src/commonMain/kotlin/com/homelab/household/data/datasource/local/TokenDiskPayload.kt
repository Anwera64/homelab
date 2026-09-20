package com.homelab.household.data.datasource.local

import kotlinx.serialization.Serializable

/** What a persistent [TokenLocalDataSource] writes to disk. */
@Serializable
internal data class TokenDiskPayload(
    val accessToken: String,
    val refreshToken: String? = null
)
