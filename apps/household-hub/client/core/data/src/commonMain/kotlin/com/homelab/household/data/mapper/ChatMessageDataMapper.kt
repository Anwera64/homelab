package com.homelab.household.data.mapper

import com.homelab.household.data.dto.ChatMessageReadDto
import com.homelab.household.domain.model.ChatMessage
import com.homelab.household.domain.model.MessageRole
import com.homelab.household.domain.model.MessageStatus

object ChatMessageDataMapper {
    fun toDomain(dto: ChatMessageReadDto): ChatMessage =
        ChatMessage(
            id = dto.id,
            sessionId = dto.session_id,
            role = parseRole(dto.role),
            content = dto.content,
            status = MessageStatus.SENT,
            createdAt = dto.created_at,
            parts = AnswerPartDataMapper.fromJson(dto.metadata_json?.get("parts")),
        )

    private fun parseRole(role: String): MessageRole =
        when (role.lowercase()) {
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            "tool" -> MessageRole.TOOL
            else -> MessageRole.USER
        }
}
