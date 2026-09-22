package com.homelab.household.domain.model

data class AgentPersonality(
    val id: String,
    val slug: String,
    val name: String,
    val description: String = "",
    val avatar: String = "🤖",
    val systemPrompt: String,
    val modelAlias: String = "qwen3:14b",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val toolPermissions: List<String> = emptyList(),
    val isBuiltin: Boolean = false,
    val isActive: Boolean = true,
    val ownerId: String? = null,
    val deletedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
