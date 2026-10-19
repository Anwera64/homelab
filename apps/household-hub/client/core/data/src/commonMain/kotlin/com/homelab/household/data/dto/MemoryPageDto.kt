package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/v1/memories/page`: the member's personal memories and every household memory. */
@Serializable
data class MemoryPageDto(
    val yours: List<MemoryPageItemDto> = emptyList(),
    val household: List<MemoryPageItemDto> = emptyList(),
    @SerialName("to_check") val to_check: Int = 0,
    val waiting: List<WaitingChangeDto> = emptyList(),
)

/** A sentence the member asked to remember over someone else's memory, still waiting on their answer. */
@Serializable
data class WaitingChangeDto(
    val id: String,
    val content: String,
    @SerialName("agent_name") val agent_name: String,
    @SerialName("created_at") val created_at: String,
    @SerialName("expires_at") val expires_at: String? = null,
    @SerialName("answerer_name") val answerer_name: String = "",
    val stored: MemoryPageItemDto,
)

@Serializable
data class MemoryPageItemDto(
    val id: String,
    val content: String,
    val scope: String = "personal",
    val origin: String = "reflection",
    @SerialName("created_at") val created_at: String,
    @SerialName("expires_at") val expires_at: String? = null,
    @SerialName("speaker_id") val speaker_id: String,
    @SerialName("speaker_name") val speaker_name: String = "",
    @SerialName("is_mine") val is_mine: Boolean = false,
    @SerialName("agent_name") val agent_name: String,
    @SerialName("agent_avatar") val agent_avatar: String,
    @SerialName("can_remove") val can_remove: Boolean = false,
    @SerialName("event_starts_at") val event_starts_at: String? = null,
    @SerialName("time_from_event") val time_from_event: String? = null,
    @SerialName("moved_from") val moved_from: String? = null,
)

/** Where a change's new sentence was read. [title] is only given to the chat's owner. */
@Serializable
data class ChangeSourceDto(
    val title: String? = null,
    @SerialName("owner_name") val owner_name: String = "",
    @SerialName("read_at") val read_at: String,
)

/** One change to check: the stored memory beside the new sentence. */
@Serializable
data class ChangeCardDto(
    val id: String,
    val content: String,
    val stored: MemoryPageItemDto,
    @SerialName("can_answer") val can_answer: Boolean = false,
    @SerialName("answerer_name") val answerer_name: String = "",
    val source: ChangeSourceDto,
)

/** `GET /api/v1/memories/proposals`. */
@Serializable
data class ChangesToCheckDto(
    @SerialName("to_check") val to_check: List<ChangeCardDto> = emptyList(),
    val waiting: List<ChangeCardDto> = emptyList(),
)

/** The body of `POST /api/v1/memories/proposals/{id}/answer`. */
@Serializable
data class ChangeAnswerDto(
    val answer: String,
)

/** `GET /api/v1/sessions/{id}/looked-over`: the sheet "Remembered from this chat". */
@Serializable
data class LookedOverDto(
    val at: String,
    @SerialName("to_check") val to_check: List<ChangeCardDto> = emptyList(),
    val waiting: List<ChangeCardDto> = emptyList(),
    val remembered: List<MemoryPageItemDto> = emptyList(),
)

/** The quiet line in a chat after the last message looked over. */
@Serializable
data class LookedOverSummaryDto(
    @SerialName("through_message_id") val through_message_id: String? = null,
    val at: String,
    val remembered: Int = 0,
    @SerialName("to_check") val to_check: Int = 0,
)
