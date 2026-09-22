package com.homelab.household.data.datasource.local

import com.homelab.household.data.dto.UserReadDto
import kotlinx.serialization.Serializable

/**
 * What a persistent [StoredSessionLocalDataSource] writes to disk.
 *
 * Every field is optional so that a payload written by an older version of the app — one that kept
 * a token and nothing else — still reads, with the member filled in the next time the hub answers.
 */
@Serializable
internal data class SessionDiskPayload(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: UserReadDto? = null
)
