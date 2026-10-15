package com.homelab.household.data.mapper

import com.homelab.household.data.dto.MemoryPageDto
import com.homelab.household.data.dto.MemoryPageItemDto
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.domain.model.MemoryPage
import com.homelab.household.domain.model.MemoryScope
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
object MemoryDataMapper {
    fun toDomain(dto: MemoryPageDto): MemoryPage =
        MemoryPage(yours = dto.yours.map(::toDomain), household = dto.household.map(::toDomain))

    fun toDomain(dto: MemoryPageItemDto): MemoryItem =
        MemoryItem(
            id = dto.id,
            content = dto.content,
            scope = if (dto.scope.equals(
                    "household",
                    ignoreCase = true,
                )
            ) {
                MemoryScope.HOUSEHOLD
            } else {
                MemoryScope.PERSONAL
            },
            origin = ORIGINS[dto.origin.lowercase()] ?: MemoryOrigin.Reflection,
            createdAt = instantOf(dto.created_at),
            expiresAt = dto.expires_at?.let(::instantOf),
            speakerId = dto.speaker_id,
            speakerName = dto.speaker_name,
            isMine = dto.is_mine,
            agentName = dto.agent_name,
            agentAvatar = dto.agent_avatar,
            canRemove = dto.can_remove,
        )

    /** The hub's SQLite hands times back without a zone; every one it writes is UTC. */
    private fun instantOf(iso: String): Instant {
        val zoned = ZONE.containsMatchIn(iso)
        return Instant.parse(if (zoned) iso else "${iso}Z")
    }

    private val ZONE = Regex("""(Z|[+-]\d{2}:?\d{2})$""")

    private val ORIGINS =
        mapOf(
            "reflection" to MemoryOrigin.Reflection,
            "tool" to MemoryOrigin.Tool,
            "manual" to MemoryOrigin.Manual,
        )
}
