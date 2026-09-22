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
import com.homelab.household.app.resources.tool_note_done
import com.homelab.household.app.resources.tool_note_failed
import com.homelab.household.app.resources.tool_note_running
import com.homelab.household.app.resources.tool_pdf_done
import com.homelab.household.app.resources.tool_pdf_failed
import com.homelab.household.app.resources.tool_pdf_running
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
)

/**
 * How a tool the hub names is put to a person.
 *
 * The one place it happens, so no screen can show `searxng_search` where it meant "Search the web"
 * (design notes §2). A tool nobody has named yet still gets words — generic ones, never its own.
 */
fun toolLabel(tool: String): ToolLabel = labels[tool] ?: generic

private val generic =
    ToolLabel(
        running = Res.string.tool_generic_running,
        done = Res.string.tool_generic_done,
        failed = Res.string.tool_generic_failed,
        icon = HearthIcon.ToolGeneric,
    )

private val labels =
    mapOf(
        "calendar_read" to
            ToolLabel(
                running = Res.string.tool_calendar_read_running,
                done = Res.string.tool_calendar_read_done,
                failed = Res.string.tool_calendar_read_failed,
                icon = HearthIcon.Schedule,
            ),
        "calendar_write" to
            ToolLabel(
                running = Res.string.tool_calendar_write_running,
                done = Res.string.tool_calendar_write_done,
                failed = Res.string.tool_calendar_write_failed,
                icon = HearthIcon.CalendarAdd,
            ),
        "searxng_search" to
            ToolLabel(
                running = Res.string.tool_search_running,
                done = Res.string.tool_search_done,
                failed = Res.string.tool_search_failed,
                icon = HearthIcon.Search,
            ),
        "pdf_reader" to
            ToolLabel(
                running = Res.string.tool_pdf_running,
                done = Res.string.tool_pdf_done,
                failed = Res.string.tool_pdf_failed,
                icon = HearthIcon.Document,
            ),
        "document_writer" to
            ToolLabel(
                running = Res.string.tool_note_running,
                done = Res.string.tool_note_done,
                failed = Res.string.tool_note_failed,
                icon = HearthIcon.Document,
            ),
    )
