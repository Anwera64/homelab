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
 * The time a memory's sentence leaves to its reader (memory spec §7). Once the hub rebuilt a memory
 * from its calendar event, its content is only the event's title, and the phone adds [startsAt] in
 * its own zone: "Dinner together on Monday 19 October, 19:30." An [allDay] event is a date.
 */
@OptIn(ExperimentalTime::class)
data class EventTime(
    val startsAt: Instant,
    val allDay: Boolean,
)

/**
 * One row on the memory page: the sentence, who said it, which agent noted it, and when. [expiresAt]
 * is set for an event memory ("until 17 Oct"). [canRemove] is true only on the member's own.
 * [eventTime] is set once the sentence was rebuilt from its event, and [movedFrom] is where that
 * event was before it last moved ("Moved in your calendar · was Sat 20:00").
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
    val eventTime: EventTime? = null,
    val movedFrom: Instant? = null,
)

/**
 * A sentence the member asked an agent to remember over someone else's memory, still waiting on
 * their answer (canvas: MemWaitingB). It isn't stored yet: agents keep using [stored] until then.
 */
@OptIn(ExperimentalTime::class)
data class WaitingChange(
    val id: String,
    val content: String,
    val agentName: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val answererName: String,
    val stored: MemoryItem,
)

/**
 * The memory page's two tabs: the member's personal memories, and every household memory. [toCheck]
 * counts the changes waiting for the member: the band on the page and the chip on Profile. [waiting]
 * are the member's own sentences waiting on someone else; their stored memories aren't in [household].
 */
data class MemoryPage(
    val yours: List<MemoryItem>,
    val household: List<MemoryItem>,
    val toCheck: Int = 0,
    val waiting: List<WaitingChange> = emptyList(),
)
