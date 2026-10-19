package com.homelab.household.presentation.memories

import com.homelab.household.domain.model.DeviceTimeZone
import com.homelab.household.domain.model.MemoryItem
import com.homelab.household.domain.model.WaitingChange
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The memory page (canvas "Memory (slice 5)"): two tabs of memories, each removable in place if it's yours. */
data class MemoriesUiState(
    val status: MemoriesStatus = MemoriesStatus.Loading,
    val tab: MemoryTab = MemoryTab.Yours,
    val yours: List<MemoryRow> = emptyList(),
    val household: List<MemoryRow> = emptyList(),
    /** The last ✕ or Undo the hub didn't take, said in a line until the next tap. */
    val failure: MemoryActionFailure? = null,
    /** Changes waiting for the member: the band at the top shows when there are any. */
    val toCheck: Int = 0,
    /** The member's sentences waiting on someone else, each beside the memory it waits on (Household tab). */
    val waiting: List<WaitingRow> = emptyList(),
) {
    /** A removed row still shows, with Undo, but no longer counts. A waiting card's stored memory does. */
    val yoursCount: Int get() = yours.count { !it.removed }
    val householdCount: Int get() = household.count { !it.removed } + waiting.size
}

/**
 * A waiting card (canvas: MemWaitingB): the member's sentence, its [age], and the [stored] memory it
 * waits on. [withdrawn] turns it into "Withdrawn · Undo" until the page is left.
 */
data class WaitingRow(
    val change: WaitingChange,
    val age: MemoryAge,
    val stored: MemoryRow,
    val withdrawn: Boolean = false,
)

enum class MemoriesStatus {
    Loading,
    Ready,
    Unreachable,
    Failed,
}

enum class MemoryTab {
    Yours,
    Household,
}

enum class MemoryActionFailure {
    RemoveUnreachable,
    RemoveFailed,
    UndoUnreachable,
    UndoFailed,
}

/**
 * One memory as its row draws it. [removed] turns the row into "Removed · Undo" until the page is left.
 * [eventTime] is the time its sentence adds, once rebuilt from its event, and [movedFrom] where that
 * event was before it moved, both on the member's phone.
 */
data class MemoryRow(
    val item: MemoryItem,
    val age: MemoryAge,
    val until: LocalDay?,
    val removed: Boolean = false,
    val eventTime: LocalEventTime? = null,
    val movedFrom: LocalEventTime? = null,
)

/** A memory's row, with its age and "until" day worked out for the member's phone. */
@OptIn(ExperimentalTime::class)
fun memoryRowOf(
    item: MemoryItem,
    deviceTimeZone: DeviceTimeZone,
    clock: Clock,
): MemoryRow {
    val now = clock.now()
    return MemoryRow(
        item = item,
        age = memoryAge(item.createdAt, now, deviceTimeZone.offsetSecondsAt(now)),
        until = item.expiresAt?.let { localDay(it, deviceTimeZone.offsetSecondsAt(it)) },
        eventTime = eventTimeOf(item, deviceTimeZone),
        movedFrom =
            item.movedFrom?.let { was ->
                localEventTime(was, deviceTimeZone.offsetSecondsAt(was), allDay = item.eventTime?.allDay == true)
            },
    )
}

/** The time an event memory's sentence adds, on the member's phone; null for the model's own words. */
@OptIn(ExperimentalTime::class)
fun eventTimeOf(
    item: MemoryItem,
    deviceTimeZone: DeviceTimeZone,
): LocalEventTime? =
    item.eventTime?.let { event ->
        localEventTime(event.startsAt, deviceTimeZone.offsetSecondsAt(event.startsAt), event.allDay)
    }
