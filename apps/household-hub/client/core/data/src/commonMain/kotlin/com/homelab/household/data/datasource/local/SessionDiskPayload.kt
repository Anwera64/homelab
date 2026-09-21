package com.homelab.household.data.datasource.local

import kotlinx.serialization.Serializable

/** What a persistent [StoredSessionLocalDataSource] writes to disk. */
@Serializable
internal data class SessionDiskPayload(
    val accessToken: String,
    val refreshToken: String? = null
)
