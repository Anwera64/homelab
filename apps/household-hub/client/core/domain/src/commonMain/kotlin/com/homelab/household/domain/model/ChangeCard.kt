package com.homelab.household.domain.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The three buttons on a change card (memory spec §6.1). */
enum class ProposalAnswer {
    KeepStored,
    KeepBoth,
    UseNew,
}

/**
 * One change to check: the [stored] memory beside the new sentence an agent read in a chat. Only the
 * member who said the stored memory can answer it ([canAnswer]); everyone else sees it read-only,
 * waiting for [answererName]. [sourceTitle] is the chat's title, given only to the chat's owner;
 * anyone else sees whose chat it was, [sourceOwnerName].
 */
@OptIn(ExperimentalTime::class)
data class ChangeCard(
    val id: String,
    val content: String,
    val stored: MemoryItem,
    val canAnswer: Boolean,
    val answererName: String,
    val sourceTitle: String?,
    val sourceOwnerName: String,
    val readAt: Instant,
)

/** The member's changes to answer, then the ones from their chats waiting for someone else. */
data class ChangesToCheck(
    val toCheck: List<ChangeCard>,
    val waiting: List<ChangeCard>,
)

/** A chat's sheet, "Remembered from this chat": what the last look-over raised and saved. */
@OptIn(ExperimentalTime::class)
data class LookedOver(
    val at: Instant,
    val toCheck: List<ChangeCard>,
    val waiting: List<ChangeCard>,
    val remembered: List<MemoryItem>,
)

/** The quiet line after [throughMessageId], the last message the look-over read. */
@OptIn(ExperimentalTime::class)
data class LookedOverLine(
    val throughMessageId: String?,
    val at: Instant,
    val remembered: Int,
    val toCheck: Int,
) {
    /** "Looked over · 10:52 · nothing new" opens nothing. */
    val isNothingNew: Boolean get() = remembered == 0 && toCheck == 0
}
