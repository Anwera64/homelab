package com.homelab.household.domain.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** How a memory got stored (memory spec §3.1). */
enum class MemoryOrigin {
    /** Noted when a chat went quiet. */
    Reflection,

    /** The member asked an agent to remember it: the "You asked" chip. */
    Tool,

    /** Added by hand. */
    Manual,
}

/**
 * One row on the memory page: the sentence, who said it, which agent noted it, and when. [expiresAt]
 * is set for an event memory ("until 17 Oct"). [canRemove] is true only on the member's own.
 */
@OptIn(ExperimentalTime::class)
data class MemoryItem(
    val id: String,
    val content: String,
    val scope: MemoryScope,
    val origin: MemoryOrigin,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val speakerId: String,
    val speakerName: String,
    val isMine: Boolean,
    val agentName: String,
    val agentAvatar: String,
    val canRemove: Boolean,
)

/** The memory page's two tabs: the member's personal memories, and every household memory. */
data class MemoryPage(
    val yours: List<MemoryItem>,
    val household: List<MemoryItem>,
)
