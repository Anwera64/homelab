package com.homelab.household.data.mapper

import com.homelab.household.data.dto.ChangeCardDto
import com.homelab.household.data.dto.ChangesToCheckDto
import com.homelab.household.data.dto.LookedOverDto
import com.homelab.household.data.dto.LookedOverSummaryDto
import com.homelab.household.data.dto.MemoryPageDto
import com.homelab.household.data.dto.MemoryPageItemDto
import com.homelab.household.domain.model.ChangeCard
import com.homelab.household.domain.model.ChangesToCheck
import com.homelab.household.domain.model.LookedOver
import com.homelab.household.domain.model.LookedOverLine
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.model.MemoryOrigin
import com.homelab.household.domain.model.MemoryPage
import com.homelab.household.domain.model.MemoryScope
import com.homelab.household.domain.model.ProposalAnswer
import com.homelab.household.domain.model.WaitingChange
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
object MemoryDataMapper {
    fun toDomain(dto: MemoryPageDto): MemoryPage =
        MemoryPage(
            yours = dto.yours.map(::toDomain),
            household = dto.household.map(::toDomain),
            toCheck = dto.to_check,
            waiting =
                dto.waiting.map { waiting ->
                    WaitingChange(
                        id = waiting.id,
                        content = waiting.content,
                        agentName = waiting.agent_name,
                        createdAt = instantOf(waiting.created_at),
                        expiresAt = waiting.expires_at?.let(::instantOf),
                        answererName = waiting.answerer_name,
                        stored = toDomain(waiting.stored),
                    )
                },
        )

    fun toDomain(dto: ChangeCardDto): ChangeCard =
        ChangeCard(
            id = dto.id,
            content = dto.content,
            stored = toDomain(dto.stored),
            canAnswer = dto.can_answer,
            answererName = dto.answerer_name,
            sourceTitle = dto.source.title,
            sourceOwnerName = dto.source.owner_name,
            readAt = instantOf(dto.source.read_at),
        )

    fun toDomain(dto: ChangesToCheckDto): ChangesToCheck =
        ChangesToCheck(toCheck = dto.to_check.map(::toDomain), waiting = dto.waiting.map(::toDomain))

    fun toDomain(dto: LookedOverDto): LookedOver =
        LookedOver(
            at = instantOf(dto.at),
            toCheck = dto.to_check.map(::toDomain),
            waiting = dto.waiting.map(::toDomain),
            remembered = dto.remembered.map(::toDomain),
        )

    fun toDomain(dto: LookedOverSummaryDto): LookedOverLine =
        LookedOverLine(
            throughMessageId = dto.through_message_id,
            at = instantOf(dto.at),
            remembered = dto.remembered,
            toCheck = dto.to_check,
        )

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

/** The hub's word for each answer on a change card. */
fun ProposalAnswer.toWire(): String =
    when (this) {
        ProposalAnswer.KeepStored -> "keep_stored"
        ProposalAnswer.KeepBoth -> "keep_both"
        ProposalAnswer.UseNew -> "use_new"
    }
