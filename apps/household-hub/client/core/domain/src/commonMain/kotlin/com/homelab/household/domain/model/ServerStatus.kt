package com.homelab.household.domain.model

sealed interface ServerStatus {
    data object Connecting : ServerStatus

    data class Online(
        val latencyMs: Long,
    ) : ServerStatus

    data class Offline(
        val reason: String,
    ) : ServerStatus
}
