package com.homelab.household.data.mapper

import com.homelab.household.data.dto.ToolApprovalDto
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolApproval

object ToolApprovalDataMapper {
    /** A row whose action this phone does not know is left out, never guessed. */
    fun toDomain(dto: ToolApprovalDto): ToolApproval? =
        ToolSummaryDataMapper.actionFromCode(dto.action)?.let { action ->
            ToolApproval(tool = dto.tool, action = action, automatic = dto.auto, alwaysAsks = dto.alwaysAsks)
        }

    /** The hub's own name for [action]. */
    fun toWire(action: ToolAction): String =
        when (action) {
            ToolAction.Create -> "create"
            ToolAction.Update -> "update"
            ToolAction.Delete -> "delete"
            ToolAction.Append -> "append"
            ToolAction.Replace -> "replace"
        }
}
