package com.homelab.household.app.screens.conversation

import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_calendar_add_automatic_ask
import com.homelab.household.app.resources.tool_calendar_add_card
import com.homelab.household.app.resources.tool_calendar_add_declined
import com.homelab.household.app.resources.tool_calendar_add_done
import com.homelab.household.app.resources.tool_calendar_add_failed
import com.homelab.household.app.resources.tool_calendar_add_now_automatic
import com.homelab.household.app.resources.tool_calendar_add_running
import com.homelab.household.app.resources.tool_calendar_change_automatic_ask
import com.homelab.household.app.resources.tool_calendar_change_card
import com.homelab.household.app.resources.tool_calendar_change_declined
import com.homelab.household.app.resources.tool_calendar_change_done
import com.homelab.household.app.resources.tool_calendar_change_failed
import com.homelab.household.app.resources.tool_calendar_change_now_automatic
import com.homelab.household.app.resources.tool_calendar_change_running
import com.homelab.household.app.resources.tool_calendar_read_done
import com.homelab.household.app.resources.tool_calendar_read_failed
import com.homelab.household.app.resources.tool_calendar_read_running
import com.homelab.household.app.resources.tool_calendar_remove_card
import com.homelab.household.app.resources.tool_calendar_remove_declined
import com.homelab.household.app.resources.tool_calendar_remove_done
import com.homelab.household.app.resources.tool_calendar_remove_failed
import com.homelab.household.app.resources.tool_calendar_remove_running
import com.homelab.household.app.resources.tool_calendar_write_done
import com.homelab.household.app.resources.tool_calendar_write_failed
import com.homelab.household.app.resources.tool_calendar_write_running
import com.homelab.household.app.resources.tool_generic_done
import com.homelab.household.app.resources.tool_generic_failed
import com.homelab.household.app.resources.tool_generic_running
import com.homelab.household.app.resources.tool_lookup_done
import com.homelab.household.app.resources.tool_lookup_failed
import com.homelab.household.app.resources.tool_lookup_running
import com.homelab.household.app.resources.tool_note_append_automatic_ask
import com.homelab.household.app.resources.tool_note_append_card
import com.homelab.household.app.resources.tool_note_append_declined
import com.homelab.household.app.resources.tool_note_append_done
import com.homelab.household.app.resources.tool_note_append_failed
import com.homelab.household.app.resources.tool_note_append_now_automatic
import com.homelab.household.app.resources.tool_note_append_running
import com.homelab.household.app.resources.tool_note_create_automatic_ask
import com.homelab.household.app.resources.tool_note_create_card
import com.homelab.household.app.resources.tool_note_create_declined
import com.homelab.household.app.resources.tool_note_create_done
import com.homelab.household.app.resources.tool_note_create_failed
import com.homelab.household.app.resources.tool_note_create_now_automatic
import com.homelab.household.app.resources.tool_note_create_running
import com.homelab.household.app.resources.tool_note_done
import com.homelab.household.app.resources.tool_note_failed
import com.homelab.household.app.resources.tool_note_replace_card
import com.homelab.household.app.resources.tool_note_replace_declined
import com.homelab.household.app.resources.tool_note_replace_done
import com.homelab.household.app.resources.tool_note_replace_failed
import com.homelab.household.app.resources.tool_note_replace_running
import com.homelab.household.app.resources.tool_note_running
import com.homelab.household.app.resources.tool_pdf_done
import com.homelab.household.app.resources.tool_pdf_failed
import com.homelab.household.app.resources.tool_pdf_running
import com.homelab.household.app.resources.tool_permission_calendar_add
import com.homelab.household.app.resources.tool_permission_calendar_change
import com.homelab.household.app.resources.tool_permission_calendar_read
import com.homelab.household.app.resources.tool_permission_calendar_remove
import com.homelab.household.app.resources.tool_permission_generic
import com.homelab.household.app.resources.tool_permission_note_append
import com.homelab.household.app.resources.tool_permission_note_create
import com.homelab.household.app.resources.tool_permission_note_replace
import com.homelab.household.app.resources.tool_permission_pdf
import com.homelab.household.app.resources.tool_permission_search
import com.homelab.household.app.resources.tool_read_page_done
import com.homelab.household.app.resources.tool_read_page_failed
import com.homelab.household.app.resources.tool_read_page_running
import com.homelab.household.app.resources.tool_search_done
import com.homelab.household.app.resources.tool_search_failed
import com.homelab.household.app.resources.tool_search_running
import com.homelab.household.domain.model.HubTool
import com.homelab.household.domain.model.ToolAction
import org.jetbrains.compose.resources.StringResource

/**
 * A tool, in words: while it runs, once it has, and if it couldn't. A write also has its approval
 * card's title and the words it leaves when it is declined; a tool that only looks has neither.
 */
data class ToolLabel(
    val running: StringResource,
    val done: StringResource,
    val failed: StringResource,
    val icon: HearthIcon,
    /** What the agent is allowed to do, in the words of the "When agents act" settings screen. */
    val permission: StringResource,
    /** What the approval card says the write will do: "Remove from your calendar". */
    val card: StringResource? = null,
    /** What a declined write leaves behind: "Not removed". */
    val declined: StringResource? = null,
    /**
     * The card's box for making this write automatic: "Auto-approve adding events from now on".
     * Null for a write that always asks (removing, replacing) and for anything that only looks.
     */
    val automaticAsk: StringResource? = null,
    /** Said once the box is ticked and the card approved: "Adding events is now automatic". */
    val nowAutomatic: StringResource? = null,
)

/**
 * How a tool the hub names is put to a person, for the [action] a write says it is doing.
 *
 * The one place it happens, so no screen can show `searxng_search` where it meant "Search the web"
 * (design notes §2), and no removal can read as "Added" (canvas: tools-map). A tool nobody has named
 * yet still gets words — generic ones, never its own. A write with no action, saved before the hub
 * said which it was, or with one its tool doesn't have, gets words that are true whatever it did.
 */
fun toolLabel(
    tool: String,
    action: ToolAction? = null,
): ToolLabel = action?.let { actionLabels[tool to it] } ?: labels[tool] ?: generic

/**
 * The permission a tool stands for — what an agent may do, not what it is doing right now — for
 * the small chips on an agent card in the picker. A write tool is named by its first action, as the
 * settings screen lists it: "Add events", "Create notes". As with [toolLabel], no raw backend name
 * ever reaches a person (design notes §2).
 */
fun toolPermissionLabel(tool: String): StringResource = toolLabel(tool, WRITE_TOOLS_FIRST_ACTION[tool]).permission

private val WRITE_TOOLS_FIRST_ACTION =
    mapOf(
        HubTool.CALENDAR_WRITE to ToolAction.Create,
        HubTool.DOCUMENT_WRITER to ToolAction.Create,
    )

private val generic =
    ToolLabel(
        running = Res.string.tool_generic_running,
        done = Res.string.tool_generic_done,
        failed = Res.string.tool_generic_failed,
        icon = HearthIcon.ToolGeneric,
        permission = Res.string.tool_permission_generic,
    )

private val labels =
    mapOf(
        HubTool.CALENDAR_READ to
            ToolLabel(
                running = Res.string.tool_calendar_read_running,
                done = Res.string.tool_calendar_read_done,
                failed = Res.string.tool_calendar_read_failed,
                icon = HearthIcon.Schedule,
                permission = Res.string.tool_permission_calendar_read,
            ),
        // Saved before the hub said which action a write was: it could have been any of them.
        HubTool.CALENDAR_WRITE to
            ToolLabel(
                running = Res.string.tool_calendar_write_running,
                done = Res.string.tool_calendar_write_done,
                failed = Res.string.tool_calendar_write_failed,
                icon = HearthIcon.Schedule,
                permission = Res.string.tool_permission_calendar_add,
            ),
        HubTool.WEB_SEARCH to
            ToolLabel(
                running = Res.string.tool_search_running,
                done = Res.string.tool_search_done,
                failed = Res.string.tool_search_failed,
                icon = HearthIcon.Search,
                permission = Res.string.tool_permission_search,
            ),
        // Reading pages and looking through them come with searching, so their permission is its.
        HubTool.READ_PAGE to
            ToolLabel(
                running = Res.string.tool_read_page_running,
                done = Res.string.tool_read_page_done,
                failed = Res.string.tool_read_page_failed,
                icon = HearthIcon.Document,
                permission = Res.string.tool_permission_search,
            ),
        HubTool.LOOKUP_SOURCES to
            ToolLabel(
                running = Res.string.tool_lookup_running,
                done = Res.string.tool_lookup_done,
                failed = Res.string.tool_lookup_failed,
                icon = HearthIcon.Search,
                permission = Res.string.tool_permission_search,
            ),
        HubTool.PDF_READER to
            ToolLabel(
                running = Res.string.tool_pdf_running,
                done = Res.string.tool_pdf_done,
                failed = Res.string.tool_pdf_failed,
                icon = HearthIcon.Document,
                permission = Res.string.tool_permission_pdf,
            ),
        HubTool.DOCUMENT_WRITER to
            ToolLabel(
                running = Res.string.tool_note_running,
                done = Res.string.tool_note_done,
                failed = Res.string.tool_note_failed,
                icon = HearthIcon.Document,
                permission = Res.string.tool_permission_note_create,
            ),
    )

/** Each write by what it does, so the card, the step and the fold all say the same thing. */
private val actionLabels =
    mapOf(
        (HubTool.CALENDAR_WRITE to ToolAction.Create) to
            ToolLabel(
                running = Res.string.tool_calendar_add_running,
                done = Res.string.tool_calendar_add_done,
                failed = Res.string.tool_calendar_add_failed,
                icon = HearthIcon.CalendarAdd,
                permission = Res.string.tool_permission_calendar_add,
                card = Res.string.tool_calendar_add_card,
                declined = Res.string.tool_calendar_add_declined,
                automaticAsk = Res.string.tool_calendar_add_automatic_ask,
                nowAutomatic = Res.string.tool_calendar_add_now_automatic,
            ),
        (HubTool.CALENDAR_WRITE to ToolAction.Update) to
            ToolLabel(
                running = Res.string.tool_calendar_change_running,
                done = Res.string.tool_calendar_change_done,
                failed = Res.string.tool_calendar_change_failed,
                icon = HearthIcon.Schedule,
                permission = Res.string.tool_permission_calendar_change,
                card = Res.string.tool_calendar_change_card,
                declined = Res.string.tool_calendar_change_declined,
                automaticAsk = Res.string.tool_calendar_change_automatic_ask,
                nowAutomatic = Res.string.tool_calendar_change_now_automatic,
            ),
        (HubTool.CALENDAR_WRITE to ToolAction.Delete) to
            ToolLabel(
                running = Res.string.tool_calendar_remove_running,
                done = Res.string.tool_calendar_remove_done,
                failed = Res.string.tool_calendar_remove_failed,
                icon = HearthIcon.CalendarRemove,
                permission = Res.string.tool_permission_calendar_remove,
                card = Res.string.tool_calendar_remove_card,
                declined = Res.string.tool_calendar_remove_declined,
            ),
        (HubTool.DOCUMENT_WRITER to ToolAction.Create) to
            ToolLabel(
                running = Res.string.tool_note_create_running,
                done = Res.string.tool_note_create_done,
                failed = Res.string.tool_note_create_failed,
                icon = HearthIcon.Document,
                permission = Res.string.tool_permission_note_create,
                card = Res.string.tool_note_create_card,
                declined = Res.string.tool_note_create_declined,
                automaticAsk = Res.string.tool_note_create_automatic_ask,
                nowAutomatic = Res.string.tool_note_create_now_automatic,
            ),
        (HubTool.DOCUMENT_WRITER to ToolAction.Append) to
            ToolLabel(
                running = Res.string.tool_note_append_running,
                done = Res.string.tool_note_append_done,
                failed = Res.string.tool_note_append_failed,
                icon = HearthIcon.Document,
                permission = Res.string.tool_permission_note_append,
                card = Res.string.tool_note_append_card,
                declined = Res.string.tool_note_append_declined,
                automaticAsk = Res.string.tool_note_append_automatic_ask,
                nowAutomatic = Res.string.tool_note_append_now_automatic,
            ),
        (HubTool.DOCUMENT_WRITER to ToolAction.Replace) to
            ToolLabel(
                running = Res.string.tool_note_replace_running,
                done = Res.string.tool_note_replace_done,
                failed = Res.string.tool_note_replace_failed,
                icon = HearthIcon.Document,
                permission = Res.string.tool_permission_note_replace,
                card = Res.string.tool_note_replace_card,
                declined = Res.string.tool_note_replace_declined,
            ),
    )
