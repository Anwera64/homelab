package com.homelab.household.domain.model

/**
 * What a tool step shows about itself (#40): a search's query and what came back, the page a read
 * was of, which [action] a write was and the event it wrote, and why a step failed.
 *
 * The hub saves it with the answer, so it is the same live and when a chat is reopened. It never
 * carries snippets, ids or error text: turning it into words is the screen's job.
 */
data class ToolSummary(
    val query: String? = null,
    val count: Int? = null,
    val sources: List<ToolSource> = emptyList(),
    val reason: ToolFailureReason? = null,
    val title: String? = null,
    /** What a write did; null for a step that only looks, or one saved before the hub said. */
    val action: ToolAction? = null,
)

/**
 * What a write tool does, as the hub names it: an event is created, updated or deleted; a note is
 * created, appended to or replaced. Kept apart so a removal is never put into words as an add.
 */
enum class ToolAction {
    Create,
    Update,
    Delete,
    Append,
    Replace,
}

/** A page a step found or read. [url] is always a web link; [title] may be empty for a page never read. */
data class ToolSource(
    val title: String,
    val url: String,
)

/** Why a step failed, as the hub names it. A reason this phone does not know yet is [Unknown]. */
enum class ToolFailureReason {
    ServiceUnavailable,
    Throttled,
    Blocked,
    Forbidden,
    TooLarge,
    NotAPage,
    Unreadable,
    NotFound,
    Unknown,
}
