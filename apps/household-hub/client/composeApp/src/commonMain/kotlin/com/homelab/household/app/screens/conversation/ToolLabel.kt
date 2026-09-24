package com.homelab.household.app.screens.conversation

import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_calendar_read_done
import com.homelab.household.app.resources.tool_calendar_read_failed
import com.homelab.household.app.resources.tool_calendar_read_running
import com.homelab.household.app.resources.tool_calendar_write_done
import com.homelab.household.app.resources.tool_calendar_write_failed
import com.homelab.household.app.resources.tool_calendar_write_running
import com.homelab.household.app.resources.tool_generic_done
import com.homelab.household.app.resources.tool_generic_failed
import com.homelab.household.app.resources.tool_generic_running
import com.homelab.household.app.resources.tool_lookup_done
import com.homelab.household.app.resources.tool_lookup_failed
import com.homelab.household.app.resources.tool_lookup_running
import com.homelab.household.app.resources.tool_note_done
import com.homelab.household.app.resources.tool_note_failed
import com.homelab.household.app.resources.tool_note_running
import com.homelab.household.app.resources.tool_pdf_done
import com.homelab.household.app.resources.tool_pdf_failed
import com.homelab.household.app.resources.tool_pdf_running
import com.homelab.household.app.resources.tool_permission_calendar_read
import com.homelab.household.app.resources.tool_permission_calendar_write
import com.homelab.household.app.resources.tool_permission_generic
import com.homelab.household.app.resources.tool_permission_note
import com.homelab.household.app.resources.tool_permission_pdf
import com.homelab.household.app.resources.tool_permission_search
import com.homelab.household.app.resources.tool_read_page_done
import com.homelab.household.app.resources.tool_read_page_failed
import com.homelab.household.app.resources.tool_read_page_running
import com.homelab.household.app.resources.tool_search_done
import com.homelab.household.app.resources.tool_search_failed
import com.homelab.household.app.resources.tool_search_running
import org.jetbrains.compose.resources.StringResource

/** A tool, in words: while it runs, once it has, and if it couldn't. */
data class ToolLabel(
    val running: StringResource,
    val done: StringResource,
    val failed: StringResource,
    val icon: HearthIcon,
    /** What the agent is allowed to do, in words a person picking an agent can weigh. */
    val permission: StringResource,
)

/**
 * How a tool the hub names is put to a person.
 *
 * The one place it happens, so no screen can show `searxng_search` where it meant "Search the web"
 * (design notes §2). A tool nobody has named yet still gets words — generic ones, never its own.
 */
fun toolLabel(tool: String): ToolLabel = labels[tool] ?: generic

/**
 * The permission a tool stands for — what an agent may do, not what it is doing right now — for
 * the small chips on an agent card in the picker. As with [toolLabel], no raw backend name ever
 * reaches a person (design notes §2).
 */
fun toolPermissionLabel(tool: String): StringResource = toolLabel(tool).permission

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
        "calendar_read" to
            ToolLabel(
                running = Res.string.tool_calendar_read_running,
                done = Res.string.tool_calendar_read_done,
                failed = Res.string.tool_calendar_read_failed,
                icon = HearthIcon.Schedule,
                permission = Res.string.tool_permission_calendar_read,
            ),
        "calendar_write" to
            ToolLabel(
                running = Res.string.tool_calendar_write_running,
                done = Res.string.tool_calendar_write_done,
                failed = Res.string.tool_calendar_write_failed,
                icon = HearthIcon.CalendarAdd,
                permission = Res.string.tool_permission_calendar_write,
            ),
        "searxng_search" to
            ToolLabel(
                running = Res.string.tool_search_running,
                done = Res.string.tool_search_done,
                failed = Res.string.tool_search_failed,
                icon = HearthIcon.Search,
                permission = Res.string.tool_permission_search,
            ),
        // Reading pages and looking through them come with searching, so their permission is its.
        "read_page" to
            ToolLabel(
                running = Res.string.tool_read_page_running,
                done = Res.string.tool_read_page_done,
                failed = Res.string.tool_read_page_failed,
                icon = HearthIcon.Document,
                permission = Res.string.tool_permission_search,
            ),
        "lookup_sources" to
            ToolLabel(
                running = Res.string.tool_lookup_running,
                done = Res.string.tool_lookup_done,
                failed = Res.string.tool_lookup_failed,
                icon = HearthIcon.Search,
                permission = Res.string.tool_permission_search,
            ),
        "pdf_reader" to
            ToolLabel(
                running = Res.string.tool_pdf_running,
                done = Res.string.tool_pdf_done,
                failed = Res.string.tool_pdf_failed,
                icon = HearthIcon.Document,
                permission = Res.string.tool_permission_pdf,
            ),
        "document_writer" to
            ToolLabel(
                running = Res.string.tool_note_running,
                done = Res.string.tool_note_done,
                failed = Res.string.tool_note_failed,
                icon = HearthIcon.Document,
                permission = Res.string.tool_permission_note,
            ),
    )
