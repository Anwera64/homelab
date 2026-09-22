package com.homelab.household.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GossipMilestoneDto(
    val id: String,
    @SerialName("source_user_id") val source_user_id: String,
    @SerialName("source_username") val source_username: String,
    @SerialName("reporting_agent_id") val reporting_agent_id: String? = null,
    @SerialName("reporting_agent_name") val reporting_agent_name: String,
    @SerialName("target_scope") val target_scope: String = "household",
    val category: String = "milestone",
    val summary: String,
    val details: Map<String, String> = emptyMap(),
    @SerialName("expires_at") val expires_at: String? = null,
    @SerialName("source_session_id") val source_session_id: String? = null,
    @SerialName("is_active") val is_active: Boolean = true,
    @SerialName("created_at") val created_at: String? = null,
    @SerialName("updated_at") val updated_at: String? = null,
)
