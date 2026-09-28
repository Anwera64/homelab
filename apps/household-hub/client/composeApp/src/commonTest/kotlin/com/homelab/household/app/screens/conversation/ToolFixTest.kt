package com.homelab.household.app.screens.conversation

import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_fix_calendar_not_connected_detail
import com.homelab.household.app.resources.tool_fix_calendar_not_connected_title
import com.homelab.household.app.resources.tool_fix_calendar_rejected_detail
import com.homelab.household.app.resources.tool_fix_calendar_rejected_title
import com.homelab.household.app.resources.tool_fix_connect_calendar
import com.homelab.household.app.resources.tool_fix_nothing_added
import com.homelab.household.app.resources.tool_fix_nothing_changed
import com.homelab.household.app.resources.tool_fix_nothing_removed
import com.homelab.household.app.resources.tool_fix_reconnect_calendar
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ToolAction
import com.homelab.household.domain.model.ToolFailureReason
import com.homelab.household.domain.model.ToolSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which failed steps the member can fix, and what the card says (canvas: ToolFailed): what broke,
 * that nothing was written, and the one button that fixes it.
 */
class ToolFixTest {
    @Test
    fun `GIVEN an add that failed on the calendar's sign-in WHEN described THEN it offers to reconnect and says nothing was added`() {
        val fix = toolFix(failed("calendar_write", ToolAction.Create, ToolFailureReason.CalendarRejected))

        assertEquals(Res.string.tool_fix_calendar_rejected_title, fix?.title)
        assertEquals(Res.string.tool_fix_calendar_rejected_detail, fix?.detail)
        assertEquals(Res.string.tool_fix_nothing_added, fix?.outcome)
        assertEquals(Res.string.tool_fix_reconnect_calendar, fix?.action)
        assertEquals(toolLabel("calendar_write", ToolAction.Create).icon, fix?.icon)
    }

    @Test
    fun `GIVEN a change or a removal that failed on the sign-in WHEN described THEN it says what was not done`() {
        assertEquals(
            Res.string.tool_fix_nothing_changed,
            toolFix(failed("calendar_write", ToolAction.Update, ToolFailureReason.CalendarRejected))?.outcome,
        )
        assertEquals(
            Res.string.tool_fix_nothing_removed,
            toolFix(failed("calendar_write", ToolAction.Delete, ToolFailureReason.CalendarRejected))?.outcome,
        )
    }

    @Test
    fun `GIVEN a calendar read that failed on the sign-in WHEN described THEN it offers to reconnect with nothing to say about writing`() {
        val fix = toolFix(failed("calendar_read", null, ToolFailureReason.CalendarRejected))

        assertEquals(Res.string.tool_fix_reconnect_calendar, fix?.action)
        assertNull(fix?.outcome)
    }

    @Test
    fun `GIVEN a calendar step with no calendar connected WHEN described THEN it offers to connect one`() {
        val fix = toolFix(failed("calendar_write", ToolAction.Create, ToolFailureReason.CalendarNotConnected))

        assertEquals(Res.string.tool_fix_calendar_not_connected_title, fix?.title)
        assertEquals(Res.string.tool_fix_calendar_not_connected_detail, fix?.detail)
        assertEquals(Res.string.tool_fix_connect_calendar, fix?.action)
        assertEquals(Res.string.tool_fix_nothing_added, fix?.outcome)
    }

    @Test
    fun `GIVEN a failure the member cannot fix from the phone WHEN described THEN there is no card`() {
        assertNull(toolFix(failed("searxng_search", null, ToolFailureReason.Throttled)))
        assertNull(toolFix(failed("calendar_read", null, ToolFailureReason.Unknown)))
        assertNull(toolFix(AnswerPart.ToolFailed("calendar_read")))
    }

    private fun failed(
        tool: String,
        action: ToolAction?,
        reason: ToolFailureReason,
    ) = AnswerPart.ToolFailed(tool, ToolSummary(action = action, reason = reason))
}
