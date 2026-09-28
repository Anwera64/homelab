package com.homelab.household.app.screens.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.homelab.household.app.components.ActionCard
import com.homelab.household.app.components.CardButtonRow
import com.homelab.household.app.components.DestructiveButton
import com.homelab.household.app.components.PrimaryButton
import com.homelab.household.app.components.SecondaryButton
import com.homelab.household.app.components.ToolRecordLine
import com.homelab.household.app.icons.HearthIcon
import com.homelab.household.app.icons.HearthIconImage
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.tool_card_approve
import com.homelab.household.app.resources.tool_card_approved
import com.homelab.household.app.resources.tool_card_day_fri
import com.homelab.household.app.resources.tool_card_day_mon
import com.homelab.household.app.resources.tool_card_day_sat
import com.homelab.household.app.resources.tool_card_day_sun
import com.homelab.household.app.resources.tool_card_day_thu
import com.homelab.household.app.resources.tool_card_day_tue
import com.homelab.household.app.resources.tool_card_day_wed
import com.homelab.household.app.resources.tool_card_decline
import com.homelab.household.app.resources.tool_card_keep
import com.homelab.household.app.resources.tool_card_month_apr
import com.homelab.household.app.resources.tool_card_month_aug
import com.homelab.household.app.resources.tool_card_month_dec
import com.homelab.household.app.resources.tool_card_month_feb
import com.homelab.household.app.resources.tool_card_month_jan
import com.homelab.household.app.resources.tool_card_month_jul
import com.homelab.household.app.resources.tool_card_month_jun
import com.homelab.household.app.resources.tool_card_month_mar
import com.homelab.household.app.resources.tool_card_month_may
import com.homelab.household.app.resources.tool_card_month_nov
import com.homelab.household.app.resources.tool_card_month_oct
import com.homelab.household.app.resources.tool_card_month_sep
import com.homelab.household.app.resources.tool_card_remove
import com.homelab.household.app.resources.tool_card_repeat_and
import com.homelab.household.app.resources.tool_card_repeat_day
import com.homelab.household.app.resources.tool_card_repeat_days
import com.homelab.household.app.resources.tool_card_repeat_month
import com.homelab.household.app.resources.tool_card_repeat_months
import com.homelab.household.app.resources.tool_card_repeat_times
import com.homelab.household.app.resources.tool_card_repeat_until
import com.homelab.household.app.resources.tool_card_repeat_week
import com.homelab.household.app.resources.tool_card_repeat_week_on
import com.homelab.household.app.resources.tool_card_repeat_weeks
import com.homelab.household.app.resources.tool_card_repeat_weeks_on
import com.homelab.household.app.resources.tool_card_repeat_year
import com.homelab.household.app.resources.tool_card_repeat_years
import com.homelab.household.app.resources.tool_card_replace
import com.homelab.household.app.resources.tool_card_scope
import com.homelab.household.app.resources.tool_card_scope_following
import com.homelab.household.app.resources.tool_card_scope_following_remove_note
import com.homelab.household.app.resources.tool_card_scope_this
import com.homelab.household.app.resources.tool_card_scope_this_remove_note
import com.homelab.household.app.resources.tool_card_untitled
import com.homelab.household.app.resources.tool_card_when
import com.homelab.household.app.resources.tool_card_when_all_day
import com.homelab.household.app.theme.HearthShapes
import com.homelab.household.app.theme.HearthTheme
import com.homelab.household.domain.model.AnswerPart
import com.homelab.household.domain.model.ProposalStatus
import org.jetbrains.compose.resources.stringResource

/**
 * A write the agent wants to make, put to the member before it happens (canvas: ToolApproveRemove).
 *
 * What it will do and to what, with Decline on the left and Approve on the right. A removal reads
 * in red and asks "Keep it" or "Remove", so saying yes to losing something is never the easy tap.
 *
 * Once answered while the step's other cards still wait, it shrinks to its record line, so what is
 * left to answer stands out; when the last one is answered the turn carries on and the hub turns
 * each into the step it became.
 */
@Composable
fun ToolApprovalCard(
    card: AnswerPart.Proposal,
    onDecide: (toolCallId: String, approved: Boolean, changes: Map<String, Any?>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = toolLabel(card.tool, card.action)
    val details = approvalCardDetails(card)
    val name = details.title ?: stringResource(Res.string.tool_card_untitled)

    when (card.status) {
        ProposalStatus.Approved -> {
            ToolRecordLine(
                icon = label.icon,
                text = stringResource(Res.string.tool_card_approved, name),
                modifier = modifier,
            )
        }

        ProposalStatus.Declined -> {
            val declined = label.declined?.let { stringResource(it) }
            ToolRecordLine(
                icon = HearthIcon.Declined,
                text = if (declined != null) "$declined$SEPARATOR$name" else name,
                modifier = modifier,
            )
        }

        ProposalStatus.Pending -> {
            PendingCard(card, label, name, details, onDecide, modifier)
        }
    }
}

@Composable
private fun PendingCard(
    card: AnswerPart.Proposal,
    label: ToolLabel,
    name: String,
    details: ApprovalCardDetails,
    onDecide: (toolCallId: String, approved: Boolean, changes: Map<String, Any?>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HearthTheme.colors
    val type = HearthTheme.typography
    val ask = cardAsk(card.action)
    val tint = if (ask == CardAsk.Approve) colors.textPrimary else colors.error

    ActionCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HearthIconImage(icon = label.icon, contentDescription = null, size = HearthTheme.size.iconSm, tint = tint)
            Text(
                text = stringResource(label.card ?: label.running),
                style = type.labelStrong,
                color = tint,
            )
        }
        // Under the title's words rather than its icon, as the canvas lines them up.
        Column(
            modifier = Modifier.padding(start = HearthTheme.size.iconSm + HearthTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
        ) {
            Text(text = name, style = type.bodyStrong, color = colors.textPrimary)
            val caption = details.whenAt?.let { whenText(it) } ?: details.preview
            if (caption != null) {
                Text(text = caption, style = type.caption, color = colors.textMuted)
            }
            details.repeat?.let { RepeatLine(it) }
        }
        var scope by remember(card.toolCallId) { mutableStateOf(details.scope) }
        scope?.let { chosen ->
            ScopeSwitch(chosen = chosen, onChoose = { scope = it })
            if (ask == CardAsk.Remove) {
                Text(
                    text =
                        stringResource(
                            if (chosen == CardScope.OnlyThis) {
                                Res.string.tool_card_scope_this_remove_note
                            } else {
                                Res.string.tool_card_scope_following_remove_note
                            },
                        ),
                    style = type.caption,
                    color = colors.textMuted,
                )
            }
        }
        val approve = { onDecide(card.toolCallId, true, scopeChange(proposed = details.scope, chosen = scope)) }
        CardButtonRow {
            val no = if (ask == CardAsk.Approve) Res.string.tool_card_decline else Res.string.tool_card_keep
            SecondaryButton(
                text = stringResource(no),
                onClick = { onDecide(card.toolCallId, false, null) },
                modifier = Modifier.weight(1f),
            )
            when (ask) {
                CardAsk.Approve -> {
                    PrimaryButton(
                        text = stringResource(Res.string.tool_card_approve),
                        onClick = approve,
                        modifier = Modifier.weight(1f),
                        lifted = false,
                    )
                }

                CardAsk.Remove, CardAsk.Replace -> {
                    DestructiveButton(
                        text =
                            stringResource(
                                if (ask ==
                                    CardAsk.Remove
                                ) {
                                    Res.string.tool_card_remove
                                } else {
                                    Res.string.tool_card_replace
                                },
                            ),
                        onClick = approve,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** "Every Tue and Thu until 24 Dec", under the first date (canvas: RepeatAdd). */
@Composable
private fun RepeatLine(repeat: CardRepeat) {
    val colors = HearthTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HearthIconImage(
            icon = HearthIcon.Repeat,
            contentDescription = null,
            size = HearthTheme.size.iconSm,
            tint = colors.textMuted,
        )
        Text(text = repeatText(repeat), style = HearthTheme.typography.caption, color = colors.textMuted)
    }
}

@Composable
private fun repeatText(repeat: CardRepeat): String {
    val n = repeat.interval
    val often =
        when (repeat.every) {
            RepeatEvery.Day -> {
                if (n ==
                    1
                ) {
                    stringResource(Res.string.tool_card_repeat_day)
                } else {
                    stringResource(Res.string.tool_card_repeat_days, n)
                }
            }

            RepeatEvery.Week -> {
                val days = dayList(repeat.weekdays)
                when {
                    days == null && n == 1 -> stringResource(Res.string.tool_card_repeat_week)
                    days == null -> stringResource(Res.string.tool_card_repeat_weeks, n)
                    n == 1 -> stringResource(Res.string.tool_card_repeat_week_on, days)
                    else -> stringResource(Res.string.tool_card_repeat_weeks_on, n, days)
                }
            }

            RepeatEvery.Month -> {
                if (n ==
                    1
                ) {
                    stringResource(Res.string.tool_card_repeat_month)
                } else {
                    stringResource(Res.string.tool_card_repeat_months, n)
                }
            }

            RepeatEvery.Year -> {
                if (n ==
                    1
                ) {
                    stringResource(Res.string.tool_card_repeat_year)
                } else {
                    stringResource(Res.string.tool_card_repeat_years, n)
                }
            }
        }
    val until = repeat.until
    val count = repeat.count
    return when {
        until != null -> {
            stringResource(
                Res.string.tool_card_repeat_until,
                often,
                until.day,
                stringResource(
                    MONTHS[
                        until.month -
                            1,
                    ],
                ),
            )
        }

        count != null -> {
            stringResource(Res.string.tool_card_repeat_times, often, count)
        }

        else -> {
            often
        }
    }
}

/** "Tue", "Tue and Thu", "Mon, Wed and Fri"; null for none. */
@Composable
private fun dayList(weekdays: List<Int>): String? {
    val names = weekdays.map { stringResource(WEEKDAYS[it]) }
    if (names.isEmpty()) return null
    if (names.size == 1) return names.single()
    return stringResource(Res.string.tool_card_repeat_and, names.dropLast(1).joinToString(", "), names.last())
}

/**
 * Only this date, or it and every later one (canvas: RepeatRemoveA). Starts on what the agent
 * understood; the member's pick goes with Approve or Remove.
 */
@Composable
private fun ScopeSwitch(
    chosen: CardScope,
    onChoose: (CardScope) -> Unit,
) {
    val colors = HearthTheme.colors
    val label = stringResource(Res.string.tool_card_scope)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(HearthShapes.item)
                .background(colors.canvas)
                .border(HearthTheme.size.hairline, colors.outline, HearthShapes.item)
                .padding(HearthTheme.spacing.xs)
                .semantics { contentDescription = label }
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xs),
    ) {
        listOf(
            CardScope.OnlyThis to Res.string.tool_card_scope_this,
            CardScope.ThisAndFollowing to Res.string.tool_card_scope_following,
        ).forEach { (option, words) ->
            val selected = option == chosen
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = HearthTheme.size.touchTarget - HearthTheme.spacing.xs)
                        .clip(HearthShapes.button)
                        .background(if (selected) colors.surface else colors.canvas)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onChoose(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(words),
                    style = HearthTheme.typography.label,
                    color = if (selected) colors.textPrimary else colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun whenText(moment: CardWhen): String {
    val day = stringResource(WEEKDAYS[moment.weekday])
    val month = stringResource(MONTHS[moment.month - 1])
    return moment.time?.let { stringResource(Res.string.tool_card_when, day, moment.day, month, it) }
        ?: stringResource(Res.string.tool_card_when_all_day, day, moment.day, month)
}

private val WEEKDAYS =
    listOf(
        Res.string.tool_card_day_mon,
        Res.string.tool_card_day_tue,
        Res.string.tool_card_day_wed,
        Res.string.tool_card_day_thu,
        Res.string.tool_card_day_fri,
        Res.string.tool_card_day_sat,
        Res.string.tool_card_day_sun,
    )

private val MONTHS =
    listOf(
        Res.string.tool_card_month_jan,
        Res.string.tool_card_month_feb,
        Res.string.tool_card_month_mar,
        Res.string.tool_card_month_apr,
        Res.string.tool_card_month_may,
        Res.string.tool_card_month_jun,
        Res.string.tool_card_month_jul,
        Res.string.tool_card_month_aug,
        Res.string.tool_card_month_sep,
        Res.string.tool_card_month_oct,
        Res.string.tool_card_month_nov,
        Res.string.tool_card_month_dec,
    )

private const val SEPARATOR = " · "
