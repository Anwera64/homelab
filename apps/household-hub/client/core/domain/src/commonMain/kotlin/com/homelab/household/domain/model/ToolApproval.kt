package com.homelab.household.domain.model

/**
 * Whether agents may do one write for the member without asking first: add events, create notes.
 *
 * [alwaysAsks] marks the writes no setting can make automatic, removing an event and replacing a
 * note, which can't be undone from the chat.
 */
data class ToolApproval(
    val tool: String,
    val action: ToolAction,
    val automatic: Boolean,
    val alwaysAsks: Boolean = false,
)

/**
 * Removing an event and replacing a note: no setting can make them automatic, so their cards never
 * offer it. The hub refuses them as well.
 */
fun alwaysAsks(action: ToolAction): Boolean = action == ToolAction.Delete || action == ToolAction.Replace
