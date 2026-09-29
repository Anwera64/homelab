package com.homelab.household.app.screens.conversation

import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_fix_calendar_fixed_add
import com.homelab.household.app.resources.tool_fix_calendar_fixed_change
import com.homelab.household.app.resources.tool_fix_calendar_fixed_check
import com.homelab.household.app.resources.tool_fix_calendar_fixed_remove
import com.homelab.household.app.resources.tool_fix_calendar_fixed_title
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
import org.jetbrains.compose.resources.StringResource

/**
 * A failed step the member can put right from the phone (canvas: ToolFailed): what broke, in
 * [title] and [detail], what the step didn't do, in [outcome], and the button that fixes it.
 *
 * Today that is only a calendar the hub can't use; every fix opens the calendar's connect flow.
 *
 * Once it has been put right, [fixed] (canvas: ToolFixedCard): the calendar is connected, [detail]
 * says what asking again will do, and there is no [action] to take any more.
 */
data class ToolFix(
    val icon: HearthIcon,
    val title: StringResource,
    val detail: StringResource,
    val outcome: StringResource?,
    val action: StringResource?,
    val fixed: Boolean = false,
)

/** The fix for [part], or null when the reason is not one the member can fix from here. */
fun toolFix(part: AnswerPart.ToolFailed): ToolFix? {
    val summary = part.summary ?: return null
    val action = summary.action
    if (summary.fixed && summary.reason in FIXED_BY_CONNECTING) {
        return ToolFix(
            icon = HearthIcon.CalendarFixed,
            title = Res.string.tool_fix_calendar_fixed_title,
            detail = askAgainOf(action),
            outcome = null,
            action = null,
            fixed = true,
        )
    }
    val (title, detail, button) =
        when (summary.reason) {
            ToolFailureReason.CalendarRejected -> {
                Triple(
                    Res.string.tool_fix_calendar_rejected_title,
                    Res.string.tool_fix_calendar_rejected_detail,
                    Res.string.tool_fix_reconnect_calendar,
                )
            }

            ToolFailureReason.CalendarNotConnected -> {
                Triple(
                    Res.string.tool_fix_calendar_not_connected_title,
                    Res.string.tool_fix_calendar_not_connected_detail,
                    Res.string.tool_fix_connect_calendar,
                )
            }

            else -> {
                return null
            }
        }
    return ToolFix(
        icon = toolLabel(part.tool, action).icon,
        title = title,
        detail = detail,
        outcome = outcomeOf(action),
        action = button,
    )
}

private val FIXED_BY_CONNECTING =
    setOf(ToolFailureReason.CalendarRejected, ToolFailureReason.CalendarNotConnected)

/** What asking again will do, now the calendar is there: the step's own action, or a look. */
private fun askAgainOf(action: ToolAction?): StringResource =
    when (action) {
        ToolAction.Create, ToolAction.Append -> Res.string.tool_fix_calendar_fixed_add
        ToolAction.Update, ToolAction.Replace -> Res.string.tool_fix_calendar_fixed_change
        ToolAction.Delete -> Res.string.tool_fix_calendar_fixed_remove
        null -> Res.string.tool_fix_calendar_fixed_check
    }

/** What a write that never ran didn't do. A step that only looks wrote nothing, so says nothing. */
private fun outcomeOf(action: ToolAction?): StringResource? =
    when (action) {
        ToolAction.Create, ToolAction.Append -> Res.string.tool_fix_nothing_added
        ToolAction.Update, ToolAction.Replace -> Res.string.tool_fix_nothing_changed
        ToolAction.Delete -> Res.string.tool_fix_nothing_removed
        null -> null
    }
